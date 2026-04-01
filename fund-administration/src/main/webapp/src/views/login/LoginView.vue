<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <el-icon :size="36" color="#409eff">
          <DataAnalysis/>
        </el-icon>
        <h2>Fund Agents</h2>
        <p class="login-subtitle">智能基金分析平台</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" :prefix-icon="User" placeholder="用户名" size="large"/>
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" :prefix-icon="Lock" placeholder="密码" show-password
                    size="large" type="password"/>
        </el-form-item>
        <el-form-item>
          <el-button :loading="loading" size="large" style="width: 100%" type="primary"
                     @click="handleLogin">登 录
          </el-button>
        </el-form-item>
      </el-form>
    </div>
    <!-- 底部版权 -->
    <div class="login-footer">Fund Analysis Agents &copy; 2026</div>
  </div>
</template>

<script lang="ts" setup>
import {reactive, ref} from 'vue'
import {useRouter} from 'vue-router'
import {ElMessage, type FormInstance} from 'element-plus'
import {Lock, User} from '@element-plus/icons-vue'
import {loginApi} from '@/api/auth'
import {login} from '@/stores/auth'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({username: '', password: ''})
const rules = {
  username: [{required: true, message: '请输入用户名', trigger: 'blur'}],
  password: [{required: true, message: '请输入密码', trigger: 'blur'}],
}

const handleLogin = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const res: any = await loginApi(form.username, form.password)
    const user = res.user || {}
    login(res.token, {
      id: user.id,
      username: user.username || form.username,
      nickname: user.nickname || user.username || form.username,
      role: user.role
    })
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch { /* request 拦截器已处理 */
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f1729 0%, #1a2744 40%, #1e3a5f 100%);
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    width: 600px;
    height: 600px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(64, 158, 255, 0.12) 0%, transparent 70%);
    top: -200px;
    right: -100px;
  }

  &::after {
    content: '';
    position: absolute;
    width: 400px;
    height: 400px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(64, 158, 255, 0.08) 0%, transparent 70%);
    bottom: -100px;
    left: -50px;
  }
}

.login-card {
  width: 400px;
  max-width: 92vw;
  padding: 48px 40px 36px;
  border-radius: 16px;
  z-index: 1;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;

  h2 {
    margin: 12px 0 4px;
    font-size: 24px;
    font-weight: 700;
    color: #1a2744;
    letter-spacing: 1px;
  }
}

.login-subtitle {
  font-size: 13px;
  color: #909399;
}

.login-footer {
  position: absolute;
  bottom: 24px;
  color: rgba(255, 255, 255, 0.35);
  font-size: 12px;
  z-index: 1;
}
</style>
