<template>
  <div class="page-container">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- AI 配置 -->
      <el-tab-pane label="AI 配置" name="ai">
        <el-card shadow="never" style="margin-bottom: 16px">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span style="font-weight: 600">AI 提供商</span>
              <el-button size="small" type="primary" @click="openProviderDialog()">
                <el-icon>
                  <Plus/>
                </el-icon>
                新增
              </el-button>
            </div>
          </template>
          <el-table :data="providers" size="small" stripe>
            <el-table-column label="代码" prop="providerCode" width="100"/>
            <el-table-column label="名称" prop="providerName" width="120"/>
            <el-table-column label="类型" prop="providerType" width="90">
              <template #default="{ row }">
                <el-tag size="small">{{ row.providerType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Base URL" min-width="180" prop="baseUrl" show-overflow-tooltip/>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <span :class="row.connectivityStatus === 'ONLINE' ? 'online' : row.connectivityStatus === 'ERROR' ? 'offline' : 'unknown'"
                      class="status-dot"/>
                {{ row.connectivityStatus || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <el-button :loading="testingCode === row.providerCode" size="small" type="success"
                           @click="doTest(row.providerCode)">测试
                </el-button>
                <el-button size="small" @click="openProviderDialog(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
        <el-card shadow="never">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span style="font-weight: 600">Agent 模型绑定</span>
              <el-button size="small" type="primary" @click="openBindingDialog()">
                <el-icon>
                  <Plus/>
                </el-icon>
                新增
              </el-button>
            </div>
          </template>
          <el-table :data="bindings" size="small" stripe>
            <el-table-column label="Agent" prop="agentId" width="160"/>
            <el-table-column label="描述" min-width="160">
              <template #default="{ row }">
                <span style="color: #909399; font-size: 12px">{{ agentDesc[row.agentId] || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="级别" prop="thinkLevel" width="110">
              <template #default="{ row }">
                <el-tag :type="row.thinkLevel === 'deep_think' ? 'danger' : 'info'" size="small">{{
                    row.thinkLevel
                  }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="提供商" prop="providerCode" width="100"/>
            <el-table-column label="模型" min-width="140" prop="modelId"/>
            <el-table-column label="启用" width="60">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70">
              <template #default="{ row }">
                <el-button size="small" @click="openBindingDialog(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
      <!-- 投资画像 -->
      <el-tab-pane label="投资画像" name="profile">
        <el-form :model="profile" label-width="140px" style="max-width: 600px">
          <el-form-item label="风险承受能力">
            <el-select v-model="profile.riskLevel" style="width: 100%">
              <el-option v-for="o in ['CONSERVATIVE','STEADY','BALANCED','AGGRESSIVE','RADICAL']" :key="o"
                         :label="riskLabels[o]" :value="o"/>
            </el-select>
          </el-form-item>
          <el-form-item label="投资期限">
            <el-select v-model="profile.investmentHorizon" style="width: 100%">
              <el-option label="短期 (< 6月)" value="SHORT"/>
              <el-option label="中期 (6月-2年)" value="MEDIUM"/>
              <el-option label="长期 (> 2年)" value="LONG"/>
            </el-select>
          </el-form-item>
          <el-form-item label="最大可承受回撤">
            <el-select v-model="profile.maxDrawdownTolerance" style="width: 100%">
              <el-option v-for="v in [5,10,20,30]" :key="v" :label="v + '%'" :value="v"/>
            </el-select>
          </el-form-item>
          <el-form-item label="基金规模偏好">
            <el-select v-model="profile.fundScalePreference" style="width: 100%">
              <el-option label="小盘 (< 20亿)" value="SMALL"/>
              <el-option label="中盘 (20-100亿)" value="MEDIUM"/>
              <el-option label="大盘 (> 100亿)" value="LARGE"/>
              <el-option label="不限" value="ANY"/>
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="doSaveProfile">保存画像</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
      <!-- 通知配置 -->
      <el-tab-pane label="通知配置" name="notification">
        <!-- Bark 设备管理 -->
        <el-card shadow="never" style="margin-bottom: 16px">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span style="font-weight: 600">Bark 设备</span>
              <el-button size="small" type="primary" @click="openChannelDialog('bark')">
                <el-icon>
                  <Plus/>
                </el-icon>
                添加设备
              </el-button>
            </div>
          </template>
          <el-empty v-if="!barkChannels.length" :image-size="60" description="暂无 Bark 设备"/>
          <el-table v-else :data="barkChannels" size="small" stripe>
            <el-table-column label="设备名称" prop="name" width="140"/>
            <el-table-column label="Device Key" min-width="200" prop="deviceKey" show-overflow-tooltip/>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                  {{ row.enabled ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" size="small" style="margin-right: 8px"
                           @change="toggleChannel(row)"/>
                <el-button :loading="testingId === row.id" size="small" type="success"
                           @click="doTestChannel(row.id)">测试
                </el-button>
                <el-button size="small" type="danger" @click="doDeleteChannel(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
        <!-- 邮箱管理 -->
        <el-card shadow="never">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span style="font-weight: 600">邮箱通知</span>
              <el-button size="small" type="primary" @click="openChannelDialog('email')">
                <el-icon>
                  <Plus/>
                </el-icon>
                添加邮箱
              </el-button>
            </div>
          </template>
          <el-empty v-if="!emailChannels.length" :image-size="60" description="暂无邮箱配置"/>
          <el-table v-else :data="emailChannels" size="small" stripe>
            <el-table-column label="邮箱地址" min-width="240" prop="email"/>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                  {{ row.enabled ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" size="small" style="margin-right: 8px"
                           @change="toggleChannel(row)"/>
                <el-button :loading="testingId === row.id" size="small" type="success"
                           @click="doTestChannel(row.id)">测试
                </el-button>
                <el-button size="small" type="danger" @click="doDeleteChannel(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
      <!-- 用户管理 -->
      <el-tab-pane label="用户管理" name="users">
        <UsersView/>
      </el-tab-pane>
    </el-tabs>
    <!-- 提供商编辑对话框 -->
    <el-dialog v-model="showProviderDialog" :title="editingProvider.id ? '编辑提供商' : '新增提供商'" width="500px">
      <el-form :model="editingProvider" label-width="100px">
        <el-form-item label="代码">
          <el-input v-model="editingProvider.providerCode"/>
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="editingProvider.providerName"/>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="editingProvider.providerType" style="width: 100%">
            <el-option label="OpenAI Compatible" value="openai"/>
            <el-option label="DashScope" value="dashscope"/>
          </el-select>
        </el-form-item>
        <el-form-item label="Base URL">
          <el-input v-model="editingProvider.baseUrl"/>
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="editingProvider.apiKeyEncrypted" placeholder="输入新 Key 或留空保持不变" show-password
                    type="password"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showProviderDialog = false">取消</el-button>
        <el-button type="primary" @click="doSaveProvider">保存</el-button>
      </template>
    </el-dialog>
    <!-- 绑定编辑对话框 -->
    <el-dialog v-model="showBindingDialog" :title="editingBinding.id ? '编辑绑定' : '新增绑定'" width="500px">
      <el-form :model="editingBinding" label-width="100px">
        <el-form-item label="Agent ID">
          <el-select v-model="editingBinding.agentId" allow-create filterable style="width: 100%">
            <el-option v-for="a in agentOptions" :key="a" :label="a + (agentDesc[a] ? ' — ' + agentDesc[a] : '')"
                       :value="a"/>
          </el-select>
        </el-form-item>
        <el-form-item label="思考级别">
          <el-select v-model="editingBinding.thinkLevel" style="width: 100%">
            <el-option label="deep_think" value="deep_think"/>
            <el-option label="quick_think" value="quick_think"/>
          </el-select>
        </el-form-item>
        <el-form-item label="提供商">
          <el-select v-model="editingBinding.providerCode" style="width: 100%">
            <el-option v-for="p in providers" :key="p.providerCode" :label="p.providerName" :value="p.providerCode"/>
          </el-select>
        </el-form-item>
        <el-form-item label="模型 ID">
          <el-input v-model="editingBinding.modelId" placeholder="如 deepseek-chat"/>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="editingBinding.enabled"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBindingDialog = false">取消</el-button>
        <el-button type="primary" @click="doSaveBinding">保存</el-button>
      </template>
    </el-dialog>
    <!-- 通知渠道对话框 -->
    <el-dialog v-model="showChannelDialog" :title="channelForm.type === 'bark' ? '添加 Bark 设备' : '添加邮箱'"
               width="480px">
      <el-form :model="channelForm" label-width="110px">
        <template v-if="channelForm.type === 'bark'">
          <el-form-item label="设备名称">
            <el-input v-model="channelForm.name" placeholder="如 iPhone 15"/>
          </el-form-item>
          <el-form-item label="Server URL">
            <el-input v-model="channelForm.serverUrl" placeholder="https://api.day.app"/>
          </el-form-item>
          <el-form-item label="Device Key">
            <el-input v-model="channelForm.deviceKey" placeholder="Bark 推送 Key"/>
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="邮箱地址">
            <el-input v-model="channelForm.email" placeholder="user@example.com"/>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="showChannelDialog = false">取消</el-button>
        <el-button :loading="savingChannel" type="primary" @click="doSaveChannel">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, reactive, ref} from 'vue'
import UsersView from '@/views/users/UsersView.vue'
import {getBindings, getProviders, saveBinding, saveProvider, testConnectivity} from '@/api/ai-config'
import {getProfile, saveProfile} from '@/api/profile'
import {
  deleteNotificationChannel,
  getNotificationChannels,
  saveNotificationChannel,
  testNotificationChannel
} from '@/api/notification'
import {ElMessage, ElMessageBox} from 'element-plus'

const activeTab = ref('ai')
const providers = ref<any[]>([])
const bindings = ref<any[]>([])
const testingCode = ref('')
const showProviderDialog = ref(false)
const showBindingDialog = ref(false)
const agentOptions = ['default', 'fund_analyst', 'technical_analyst', 'industry_analyst',
  'manager_analyst', 'sentiment_analyst', 'news_analyst', 'trader', 'risk_manager', 'report_generator']
const agentDesc: Record<string, string> = {
  default: '默认（未指定 Agent 时使用）',
  fund_analyst: '基金分析师 — 基本面与业绩评估',
  technical_analyst: '技术分析师 — 趋势与技术指标',
  industry_analyst: '行业分析师 — 行业前景与竞争',
  manager_analyst: '基金经理分析 — 管理能力评估',
  sentiment_analyst: '情绪分析师 — 市场情绪研判',
  news_analyst: '新闻分析师 — 舆情与事件影响',
  trader: '交易决策 — 买卖时机与仓位',
  risk_manager: '风控经理 — 风险评估与控制',
  report_generator: '报告生成 — 汇总分析报告',
}
const riskLabels: Record<string, string> = {
  CONSERVATIVE: '保守型', STEADY: '稳健型', BALANCED: '平衡型', AGGRESSIVE: '进取型', RADICAL: '激进型'
}
const editingProvider = reactive<any>({
  providerCode: '',
  providerName: '',
  providerType: 'openai',
  baseUrl: '',
  apiKeyEncrypted: ''
})
const editingBinding = reactive<any>({
  agentId: 'default',
  thinkLevel: 'deep_think',
  providerCode: '',
  modelId: '',
  enabled: true
})
const profile = reactive<any>({
  riskLevel: 'BALANCED',
  investmentHorizon: 'MEDIUM',
  maxDrawdownTolerance: 20,
  fundScalePreference: 'ANY'
})

// 通知渠道
const channels = ref<any[]>([])
const barkChannels = computed(() => channels.value.filter(c => c.type === 'bark'))
const emailChannels = computed(() => channels.value.filter(c => c.type === 'email'))
const showChannelDialog = ref(false)
const savingChannel = ref(false)
const testingId = ref<number | null>(null)
const channelForm = reactive<any>({type: 'bark', name: '', serverUrl: 'https://api.day.app', deviceKey: '', email: ''})

const loadData = async () => {
  try {
    providers.value = (await getProviders()) as any
  } catch {
  }
  try {
    bindings.value = (await getBindings()) as any
  } catch {
  }
  try {
    const p = (await getProfile()) as any
    if (p) Object.assign(profile, p)
  } catch {
  }
  try {
    channels.value = (await getNotificationChannels()) as any || []
  } catch {
  }
}
onMounted(loadData)

const doTest = async (code: string) => {
  testingCode.value = code
  try {
    await testConnectivity(code);
    ElMessage.success(`${code} 连通成功`);
    await loadData()
  } catch (e: any) {
    ElMessage.error(`测试失败: ${e.message}`)
  } finally {
    testingCode.value = ''
  }
}
const openProviderDialog = (row?: any) => {
  if (row) Object.assign(editingProvider, {...row, apiKeyEncrypted: ''})
  else Object.assign(editingProvider, {
    id: null,
    providerCode: '',
    providerName: '',
    providerType: 'openai',
    baseUrl: '',
    apiKeyEncrypted: ''
  })
  showProviderDialog.value = true
}
const doSaveProvider = async () => {
  try {
    await saveProvider(editingProvider);
    ElMessage.success('保存成功');
    showProviderDialog.value = false;
    await loadData()
  } catch {
    ElMessage.error('保存失败')
  }
}
const openBindingDialog = (row?: any) => {
  if (row) Object.assign(editingBinding, row)
  else Object.assign(editingBinding, {
    id: null,
    agentId: 'default',
    thinkLevel: 'deep_think',
    providerCode: providers.value[0]?.providerCode || '',
    modelId: '',
    enabled: true
  })
  showBindingDialog.value = true
}
const doSaveBinding = async () => {
  try {
    await saveBinding(editingBinding);
    ElMessage.success('保存成功');
    showBindingDialog.value = false;
    await loadData()
  } catch {
    ElMessage.error('保存失败')
  }
}
const doSaveProfile = async () => {
  try {
    await saveProfile(profile);
    ElMessage.success('画像已保存')
  } catch {
    ElMessage.error('保存失败')
  }
}
// 通知渠道操作
const openChannelDialog = (type: 'bark' | 'email') => {
  Object.assign(channelForm, {type, name: '', serverUrl: 'https://api.day.app', deviceKey: '', email: ''})
  showChannelDialog.value = true
}
const doSaveChannel = async () => {
  if (channelForm.type === 'bark' && !channelForm.deviceKey?.trim()) return ElMessage.warning('请输入 Device Key')
  if (channelForm.type === 'email' && !channelForm.email?.trim()) return ElMessage.warning('请输入邮箱地址')
  savingChannel.value = true
  try {
    await saveNotificationChannel({...channelForm, enabled: true})
    ElMessage.success('保存成功');
    showChannelDialog.value = false;
    await loadData()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    savingChannel.value = false
  }
}
const doDeleteChannel = async (id: number) => {
  await ElMessageBox.confirm('确定删除该通知渠道？', '提示', {type: 'warning'})
  try {
    await deleteNotificationChannel(id);
    await loadData();
    ElMessage.success('已删除')
  } catch { /* cancelled */
  }
}
const doTestChannel = async (id: number) => {
  testingId.value = id
  try {
    await testNotificationChannel(id);
    ElMessage.success('测试消息已发送')
  } catch {
    ElMessage.error('测试失败')
  } finally {
    testingId.value = null
  }
}
const toggleChannel = async (row: any) => {
  try {
    await saveNotificationChannel(row);
    ElMessage.success(row.enabled ? '已启用' : '已禁用')
  } catch {
    row.enabled = !row.enabled;
    ElMessage.error('操作失败')
  }
}
</script>
