<template>
  <div>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span style="font-weight: 600">用户管理</span>
          <el-button type="primary" @click="openCreate">
            <el-icon>
              <Plus/>
            </el-icon>
            新建用户
          </el-button>
        </div>
      </template>
      <!-- 统计卡片 -->
      <div class="user-stats">
        <div class="user-stat-item">
          <div class="user-stat-value">{{ users.length }}</div>
          <div class="user-stat-label">总用户数</div>
        </div>
        <div class="user-stat-item">
          <div class="user-stat-value" style="color:#67c23a">{{ users.filter(u => u.status === 1).length }}</div>
          <div class="user-stat-label">启用</div>
        </div>
        <div class="user-stat-item">
          <div class="user-stat-value" style="color:#f56c6c">{{ users.filter(u => u.status === 0).length }}</div>
          <div class="user-stat-label">禁用</div>
        </div>
        <div class="user-stat-item">
          <div class="user-stat-value" style="color:#e6a23c">{{ users.filter(u => u.role === 'ADMIN').length }}</div>
          <div class="user-stat-label">管理员</div>
        </div>
      </div>
      <!-- 用户表格 -->
      <el-table v-loading="loading" :data="users" stripe>
        <el-table-column label="ID" prop="id" width="60"/>
        <el-table-column label="用户名" min-width="100" prop="username"/>
        <el-table-column label="邮箱" min-width="160" prop="email" show-overflow-tooltip>
          <template #default="{row}">{{ row.email || '-' }}</template>
        </el-table-column>
        <el-table-column label="手机号" prop="phone" width="130">
          <template #default="{row}">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" prop="role" width="100">
          <template #default="{row}">
            <el-tag :type="row.role === 'ADMIN' ? 'warning' : 'info'" size="small">
              {{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="80">
          <template #default="{row}">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近登录" prop="lastLoginTime" show-overflow-tooltip width="170">
          <template #default="{row}">{{ row.lastLoginTime || '从未登录' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createdAt" show-overflow-tooltip width="170"/>
        <el-table-column fixed="right" label="操作" width="260">
          <template #default="{row}">
            <div style="display: flex; align-items: center; gap: 6px; flex-wrap: nowrap">
              <el-button size="small" @click="openEdit(row)">编辑</el-button>
              <el-button size="small" type="warning" @click="openResetPwd(row)">重置密码</el-button>
              <el-popconfirm title="确定删除该用户？" @confirm="handleDelete(row.id)">
                <template #reference>
                  <el-button :disabled="row.username === 'admin'" size="small" type="danger">删除</el-button>
                </template>
              </el-popconfirm>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '新建用户'" destroy-on-close width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" placeholder="请输入用户名"/>
        </el-form-item>
        <el-form-item v-if="!isEdit" label="密码" prop="password">
          <el-input v-model="form.password" placeholder="请输入密码" show-password type="password"/>
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱"/>
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号"/>
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="普通用户" value="USER"/>
            <el-option label="管理员" value="ADMIN"/>
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.statusBool" active-text="启用" inactive-text="禁用"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button :loading="submitting" type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
    <!-- 重置密码对话框 -->
    <el-dialog v-model="pwdDialogVisible" destroy-on-close title="重置密码" width="400px">
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="80px">
        <el-form-item label="用户">
          <el-input :model-value="pwdForm.username" disabled/>
        </el-form-item>
        <el-form-item label="新密码" prop="password">
          <el-input v-model="pwdForm.password" placeholder="请输入新密码" show-password type="password"/>
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPwd">
          <el-input v-model="pwdForm.confirmPwd" placeholder="请再次输入" show-password type="password"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialogVisible = false">取消</el-button>
        <el-button :loading="submitting" type="primary" @click="handleResetPwd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script lang="ts" setup>
import {onMounted, reactive, ref} from 'vue'
import {createUser, deleteUser, getUsers, updateUser} from '@/api/auth'
import request from '@/api/request'
import {ElMessage, type FormInstance, type FormRules} from 'element-plus'

const users = ref<any[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const pwdDialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const pwdFormRef = ref<FormInstance>()
const editingId = ref<number>(0)

const form = reactive({username: '', password: '', email: '', phone: '', role: 'USER', statusBool: true})
const pwdForm = reactive({userId: 0, username: '', password: '', confirmPwd: ''})

const rules: FormRules = {
  username: [{required: true, message: '请输入用户名', trigger: 'blur'},
    {min: 2, max: 30, message: '2-30个字符', trigger: 'blur'}],
  password: [{required: true, message: '请输入密码', trigger: 'blur'},
    {min: 6, max: 50, message: '至少6个字符', trigger: 'blur'}],
  email: [{type: 'email', message: '邮箱格式不正确', trigger: 'blur'}],
}
const pwdRules: FormRules = {
  password: [{required: true, message: '请输入新密码', trigger: 'blur'},
    {min: 6, max: 50, message: '至少6个字符', trigger: 'blur'}],
  confirmPwd: [{required: true, message: '请确认密码', trigger: 'blur'},
    {validator: (_r, v, cb) => v === pwdForm.password ? cb() : cb(new Error('两次密码不一致')), trigger: 'blur'}],
}

const fetchUsers = async () => {
  loading.value = true
  try {
    users.value = (await getUsers()) as any || []
  } catch {
  }
  loading.value = false
}
const openCreate = () => {
  isEdit.value = false;
  editingId.value = 0
  Object.assign(form, {username: '', password: '', email: '', phone: '', role: 'USER', statusBool: true})
  dialogVisible.value = true
}
const openEdit = (row: any) => {
  isEdit.value = true;
  editingId.value = row.id
  Object.assign(form, {
    username: row.username, password: '', email: row.email || '',
    phone: row.phone || '', role: row.role, statusBool: row.status === 1
  })
  dialogVisible.value = true
}
const openResetPwd = (row: any) => {
  Object.assign(pwdForm, {userId: row.id, username: row.username, password: '', confirmPwd: ''})
  pwdDialogVisible.value = true
}
const handleSubmit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateUser(editingId.value, {
        email: form.email,
        phone: form.phone,
        role: form.role,
        status: form.statusBool ? 1 : 0
      })
      ElMessage.success('更新成功')
    } else {
      await createUser({
        username: form.username,
        password: form.password,
        email: form.email,
        phone: form.phone,
        role: form.role
      })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await fetchUsers()
  } catch {
  }
  submitting.value = false
}
const handleResetPwd = async () => {
  await pwdFormRef.value?.validate()
  submitting.value = true
  try {
    await request.put(`/users/${pwdForm.userId}/password`, {password: pwdForm.password})
    ElMessage.success('密码重置成功')
    pwdDialogVisible.value = false
  } catch {
  }
  submitting.value = false
}
const handleDelete = async (id: number) => {
  try {
    await deleteUser(id);
    ElMessage.success('删除成功');
    await fetchUsers()
  } catch {
  }
}
onMounted(fetchUsers)
</script>

<style lang="scss" scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.user-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;

  .user-stat-item {
    text-align: center;
    padding: 16px;
    background: #f5f7fa;
    border-radius: 8px;

    .user-stat-value {
      font-size: 28px;
      font-weight: 700;
      color: #409eff;
    }

    .user-stat-label {
      font-size: 13px;
      color: #909399;
      margin-top: 4px;
    }
  }
}

@media (max-width: 767px) {
  .user-stats {
    grid-template-columns: repeat(2, 1fr);
    gap: 10px;

    .user-stat-item {
      padding: 12px;

      .user-stat-value {
        font-size: 20px;
      }
    }
  }
}
</style>
