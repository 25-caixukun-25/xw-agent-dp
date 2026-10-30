<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { loginByCode, sendCode } from '@/api'
import { useUserStore } from '@/stores/user'

const phone = ref('')
const code = ref('')
const sending = ref(false)
const loading = ref(false)
const countdown = ref(0)
let timer: number | undefined

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

function isValidPhone(value: string) {
  return /^1[3-9]\d{9}$/.test(value)
}

async function handleSend() {
  if (!isValidPhone(phone.value)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  sending.value = true
  try {
    await sendCode(phone.value)
    ElMessage.success('验证码已发送，请查看后端控制台日志')
    countdown.value = 60
    timer = window.setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0 && timer) {
        window.clearInterval(timer)
        timer = undefined
      }
    }, 1000)
  } finally {
    sending.value = false
  }
}

async function handleLogin() {
  if (!isValidPhone(phone.value)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  if (!/^\d{6}$/.test(code.value)) {
    ElMessage.warning('请输入 6 位验证码')
    return
  }
  loading.value = true
  try {
    const token = await loginByCode(phone.value, code.value)
    userStore.setToken(token)
    await userStore.loadUser()
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    router.replace(redirect)
  } finally {
    loading.value = false
  }
}

onBeforeUnmount(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <aside class="intro">
        <h1>智觅生活<span>AI</span></h1>
        <p class="slogan">说不清想找什么的时候，<br />问我。</p>
        <ul class="points">
          <li>一句话说出需求，AI 帮你筛店</li>
          <li>语义搜索：找「安静适合约会」的地方</li>
          <li>实时查询真实数据，不编造</li>
        </ul>
      </aside>

      <section class="form-panel">
        <h2>手机号登录</h2>
        <p class="hint">未注册的手机号将自动创建账号</p>

        <el-form label-position="top" @submit.prevent>
          <el-form-item label="手机号">
            <el-input
              v-model="phone"
              size="large"
              maxlength="11"
              placeholder="请输入 11 位手机号"
              clearable
            />
          </el-form-item>

          <el-form-item label="验证码">
            <div class="code-row">
              <el-input
                v-model="code"
                size="large"
                maxlength="6"
                placeholder="6 位验证码"
                @keyup.enter="handleLogin"
              />
              <el-button
                size="large"
                :disabled="countdown > 0"
                :loading="sending"
                @click="handleSend"
              >
                {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
              </el-button>
            </div>
          </el-form-item>

          <el-button
            type="primary"
            size="large"
            class="submit"
            :loading="loading"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form>

        <p class="tip">演示环境不发短信，验证码打印在后端控制台日志里</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f6f7f9;
  padding: 24px;
}

.login-card {
  width: 900px;
  max-width: 100%;
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid rgba(0, 0, 0, 0.05);
}

.intro {
  padding: 48px 40px;
  background: #fff7f3;
  border-right: 1px solid rgba(255, 102, 51, 0.12);
}

.intro h1 {
  margin: 0;
  font-size: 30px;
  letter-spacing: 0.5px;
}

.intro h1 span {
  margin-left: 8px;
  padding: 2px 8px;
  font-size: 14px;
  color: #fff;
  background: var(--brand);
  border-radius: 8px;
  vertical-align: middle;
}

.slogan {
  margin: 20px 0 28px;
  font-size: 20px;
  line-height: 1.6;
  color: var(--text-main);
}

.points {
  margin: 0;
  padding-left: 18px;
  color: var(--text-sub);
  font-size: 14px;
  line-height: 2;
}

.form-panel {
  padding: 48px 40px;
}

.form-panel h2 {
  margin: 0 0 6px;
  font-size: 20px;
}

.hint {
  margin: 0 0 24px;
  font-size: 13px;
  color: var(--text-sub);
}

.code-row {
  display: flex;
  gap: 12px;
  width: 100%;
}

.submit {
  width: 100%;
  margin-top: 8px;
}

.tip {
  margin-top: 18px;
  font-size: 12px;
  color: #9ca3af;
  line-height: 1.6;
}

@media (max-width: 760px) {
  .login-card {
    grid-template-columns: 1fr;
  }

  .intro {
    display: none;
  }
}
</style>
