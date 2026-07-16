<template>
  <div class="login-body">
    <div class="bg"></div>

    <div class="login-panel">
      <!-- 品牌 -->
      <div class="brand-area">
        <div class="brand-icon"><span class="iconfont icon-pan"></span></div>
        <div class="brand-name">知识资产管理平台</div>
      </div>

      <!-- Tab 切换 -->
      <div class="login-tabs">
        <span :class="['tab-item', opType === 1 ? 'active' : '']" @click="showPanel(1)">登录</span>
        <span :class="['tab-item', opType === 0 ? 'active' : '']" @click="showPanel(0)">注册</span>
        <span :class="['tab-item', opType === 2 ? 'active' : '']" @click="showPanel(2)">重置密码</span>
      </div>

      <el-form class="login-form" :model="formData" :rules="rules" ref="formDataRef">
        <!-- 邮箱 -->
        <el-form-item prop="email">
          <el-input size="large" clearable placeholder="邮箱" v-model.trim="formData.email" maxlength="150">
            <template #prefix><span class="iconfont icon-account"></span></template>
          </el-input>
        </el-form-item>

        <!-- 登录密码 -->
        <el-form-item prop="password" v-if="opType === 1">
          <el-input type="password" size="large" placeholder="密码" v-model.trim="formData.password" show-password>
            <template #prefix><span class="iconfont icon-password"></span></template>
          </el-input>
        </el-form-item>

        <!-- 注册 / 重置（与 oldSmartDrive 顺序一致：邮箱验证码 → 昵称 → 密码 → 确认密码） -->
        <template v-if="opType === 0 || opType === 2">
          <el-form-item prop="emailCode">
            <div class="email-code-row">
              <el-input size="large" placeholder="邮箱验证码" v-model.trim="formData.emailCode" maxlength="6">
                <template #prefix><span class="iconfont icon-checkcode"></span></template>
              </el-input>
              <el-button class="send-btn" size="large" @click="getEmailCode" :loading="sendingCode" :disabled="codeCountdown > 0">
                {{ codeBtnText }}
              </el-button>
            </div>
          </el-form-item>
          <el-form-item prop="nickName" v-if="opType === 0">
            <el-input size="large" clearable placeholder="昵称" v-model.trim="formData.nickName" maxlength="20">
              <template #prefix><span class="iconfont icon-account"></span></template>
            </el-input>
          </el-form-item>
          <el-form-item prop="registerPassword">
            <el-input type="password" size="large" placeholder="密码（8-18位，含数字字母特殊字符）" v-model.trim="formData.registerPassword" show-password>
              <template #prefix><span class="iconfont icon-password"></span></template>
            </el-input>
          </el-form-item>
          <el-form-item prop="reRegisterPassword" v-if="opType === 0">
            <el-input type="password" size="large" placeholder="确认密码" v-model.trim="formData.reRegisterPassword" show-password>
              <template #prefix><span class="iconfont icon-password"></span></template>
            </el-input>
          </el-form-item>
        </template>

        <!-- 图片验证码（所有模式） -->
        <el-form-item prop="checkCode">
          <div class="check-code-row">
            <el-input size="large" placeholder="验证码" v-model.trim="formData.checkCode" maxlength="5" @keyup.enter="doSubmit">
              <template #prefix><span class="iconfont icon-checkcode"></span></template>
            </el-input>
            <img :src="checkCodeUrl" class="check-code-img" @click="changeCheckCode(0)" title="点击刷新" />
          </div>
        </el-form-item>

        <!-- 登录：记住我 -->
        <el-form-item v-if="opType === 1">
          <el-checkbox v-model="formData.rememberMe" size="small">记住我</el-checkbox>
        </el-form-item>
        <el-form-item v-if="opType === 0" class="extra-row">
          <a class="a-link" @click="showPanel(1)">← 返回登录</a>
        </el-form-item>
        <el-form-item v-if="opType === 2" class="extra-row">
          <a class="a-link" @click="showPanel(1)">← 返回登录</a>
        </el-form-item>

        <!-- 提交按钮 -->
        <el-form-item>
          <el-button type="primary" class="op-btn" size="large" @click="doSubmit" :loading="submitting">
            {{ opType === 0 ? '注册' : opType === 2 ? '重置密码' : '登录' }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 发送邮箱验证码弹窗（与 oldSmartDrive 逻辑一致） -->
    <Dialog
      :show="dialogConfig4SendMailCode.show"
      :title="dialogConfig4SendMailCode.title"
      :buttons="dialogConfig4SendMailCode.buttons"
      width="500px"
      :showCancel="false"
      @close="dialogConfig4SendMailCode.show = false"
    >
      <el-form
        :model="formData4SendMailCode"
        :rules="rules"
        ref="formData4SendMailCodeRef"
        label-width="80px"
        @submit.prevent
      >
        <el-form-item label="邮箱">
          {{ formData.email }}
        </el-form-item>
        <el-form-item label="验证码" prop="checkCode">
          <div class="check-code-row">
            <el-input size="large" placeholder="请输入验证码" v-model.trim="formData4SendMailCode.checkCode">
              <template #prefix><span class="iconfont icon-checkcode"></span></template>
            </el-input>
            <img
              :src="checkCodeUrl4SendMailCode"
              class="check-code-img"
              @click="changeCheckCode(1)"
              title="点击刷新"
            />
          </div>
        </el-form-item>
      </el-form>
    </Dialog>
  </div>
</template>

<script setup>
import { ref, reactive, getCurrentInstance, nextTick, onMounted } from "vue";
import { useRouter, useRoute } from "vue-router";
import md5 from "js-md5";

const { proxy } = getCurrentInstance();
const router = useRouter();
const route = useRoute();

const API = {
  checkCode: "/api/auth/checkCode",
  sendEmailCode: "/auth/sendEmailCode",
  register: "/auth/register",
  login: "/auth/login",
  resetPwd: "/auth/resetPwd",
};

// 0=注册 1=登录 2=重置密码
const opType = ref(1);
const submitting = ref(false);
const sendingCode = ref(false);
const codeCountdown = ref(0);
let countdownTimer = null;
const codeBtnText = ref("获取验证码");

onMounted(() => {
  showPanel(1);
  if (route.query.kicked === "1") {
    proxy.Message.warning("账号已在别处登录，请重新登录");
  }
});

const showPanel = (type) => {
  opType.value = type;
  resetForm();
};

const formData = ref({});
const formDataRef = ref();

// ---- 表单校验（与 oldSmartDrive 完全一致）----
const checkRePassword = (_rule, value, callback) => {
  if (value !== formData.value.registerPassword) {
    callback(new Error("两次输入的密码不一致"));
  } else {
    callback();
  }
};

const rules = {
  email: [
    { required: true, message: "请输入邮箱" },
    { validator: proxy.Verify.email, message: "请输入正确的邮箱" },
  ],
  password: [{ required: true, message: "请输入密码" }],
  registerPassword: [
    { required: true, message: "请输入密码" },
    {
      validator: proxy.Verify.password,
      message: "密码只能是数字，字母，特殊字符 8-18位",
    },
  ],
  reRegisterPassword: [
    { required: true, message: "请再次输入密码" },
    { validator: checkRePassword, message: "两次输入的密码不一致" },
  ],
  emailCode: [{ required: true, message: "请输入邮箱验证码" }],
  nickName: [{ required: true, message: "请输入昵称" }],
  checkCode: [{ required: true, message: "请输入图片验证码" }],
};

// ---- 图片验证码（双通道：type=0 主表单，type=1 邮箱弹窗）----
const checkCodeUrl = ref("");
const checkCodeUrl4SendMailCode = ref("");
const changeCheckCode = (type) => {
  const url = API.checkCode + "?type=" + type + "&time=" + Date.now();
  if (type === 0) {
    checkCodeUrl.value = url;
  } else {
    checkCodeUrl4SendMailCode.value = url;
  }
};

// ---- 发送邮箱验证码弹窗 ----
const formData4SendMailCode = ref({});
const formData4SendMailCodeRef = ref();
const dialogConfig4SendMailCode = reactive({
  show: false,
  title: "发送邮箱验证码",
  buttons: [
    {
      type: "primary",
      text: "发送验证码",
      click: () => {
        sendEmailCode();
      },
    },
  ],
});

// 打开弹窗：先校验邮箱
const getEmailCode = () => {
  formDataRef.value.validateField("email", (valid) => {
    if (!valid) return;
    dialogConfig4SendMailCode.show = true;
    nextTick(() => {
      changeCheckCode(1);
      formData4SendMailCodeRef.value.resetFields();
      formData4SendMailCode.value = { email: formData.value.email };
    });
  });
};

// 弹窗内发送邮箱验证码
const sendEmailCode = () => {
  formData4SendMailCodeRef.value.validate(async (valid) => {
    if (!valid) return;

    sendingCode.value = true;
    try {
      const params = Object.assign({}, formData4SendMailCode.value);
      params.type = opType.value === 0 ? 0 : 1;
      const result = await proxy.Request({
        url: API.sendEmailCode,
        params: params,
        errorCallback: () => changeCheckCode(1),
      });
      if (!result) return;

      proxy.Message.success("验证码发送成功，请登录邮箱查看");
      dialogConfig4SendMailCode.show = false;

      // 60 秒倒计时
      codeCountdown.value = 60;
      codeBtnText.value = "60s 后重发";
      countdownTimer = setInterval(() => {
        codeCountdown.value--;
        codeBtnText.value = `${codeCountdown.value}s 后重发`;
        if (codeCountdown.value <= 0) {
          clearInterval(countdownTimer);
          codeBtnText.value = "获取验证码";
        }
      }, 1000);
    } finally {
      sendingCode.value = false;
    }
  });
};

// ---- 重置表单（与 oldSmartDrive 一致）----
const resetForm = () => {
  nextTick(() => {
    changeCheckCode(0);
    formDataRef.value?.resetFields();
    formData.value = {};
    clearInterval(countdownTimer);
    codeBtnText.value = "获取验证码";
    codeCountdown.value = 0;

    // 登录时恢复记住的账号
    if (opType.value === 1) {
      const cookieLoginInfo = proxy.VueCookies.get("loginInfo");
      if (cookieLoginInfo) {
        formData.value = cookieLoginInfo;
      }
    }
  });
};

// ---- 提交（与 oldSmartDrive 完全一致）----
const doSubmit = () => {
  formDataRef.value.validate(async (valid) => {
    if (!valid) return;

    let params = {};
    Object.assign(params, formData.value);

    // 注册/重置：将 registerPassword 映射为 password
    if (opType.value === 0 || opType.value === 2) {
      params.password = params.registerPassword;
      delete params.registerPassword;
      delete params.reRegisterPassword;
    }

    // 登录：密码 md5（与 cookie 比对避免二次加密）
    if (opType.value === 1) {
      let cookieLoginInfo = proxy.VueCookies.get("loginInfo");
      let cookiePassword = cookieLoginInfo == null ? null : cookieLoginInfo.password;
      if (params.password !== cookiePassword) {
        params.password = md5(params.password);
      }
    }

    let url;
    if (opType.value === 0) {
      url = API.register;
    } else if (opType.value === 1) {
      url = API.login;
    } else {
      url = API.resetPwd;
    }

    submitting.value = true;
    try {
      const result = await proxy.Request({
        url: url,
        params: params,
        errorCallback: () => changeCheckCode(0),
      });
      if (!result) return;

      if (opType.value === 0) {
        proxy.Message.success("注册成功，请登录");
        showPanel(1);
      } else if (opType.value === 1) {
        // 记住我
        if (params.rememberMe || formData.value.rememberMe) {
          const loginInfo = {
            email: params.email,
            password: params.password,
            rememberMe: true,
          };
          proxy.VueCookies.set("loginInfo", loginInfo, "7d");
        } else {
          proxy.VueCookies.remove("loginInfo");
        }
        // 存储 token
        localStorage.setItem("accessToken", result.data.accessToken);
        localStorage.setItem("refreshToken", result.data.refreshToken);
        proxy.VueCookies.set("userInfo", result.data.userInfo, 0);
        proxy.Message.success("登录成功");
        const redirectUrl = route.query.redirectUrl || "/";
        router.push(redirectUrl);
      } else {
        proxy.Message.success("重置密码成功，请登录");
        showPanel(1);
      }
    } finally {
      submitting.value = false;
    }
  });
};
</script>

<style lang="scss" scoped>
.login-body {
  height: 100vh;
  background: var(--bg-page);
  display: flex;
  overflow: hidden;

  .bg {
    flex: 1;
    background: url("../assets/login_img.png") center / 600px no-repeat;
    opacity: 0.55;
  }

  .login-panel {
    width: 400px;
    margin-right: 8%;
    margin-top: calc((100vh - 520px) / 2);

    .brand-area {
      text-align: center;
      margin-bottom: 24px;

      .brand-icon .iconfont {
        font-size: 30px;
        color: #fff;
        background: var(--primary);
        width: 44px;
        height: 44px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border-radius: var(--radius-sm);
        animation: pulse 2.5s infinite;
      }

      .brand-name {
        font-size: 18px;
        font-weight: 600;
        color: var(--text-primary);
        margin-top: 10px;
      }
    }

    .login-tabs {
      display: flex;
      gap: 28px;
      justify-content: center;
      margin-bottom: 22px;

      .tab-item {
        font-size: 14px;
        color: var(--text-tertiary);
        cursor: pointer;
        padding: 4px 0;
        position: relative;
        transition: color var(--transition-fast);
        user-select: none;

        &::after {
          content: '';
          position: absolute;
          bottom: -2px;
          left: 0;
          right: 0;
          height: 2px;
          background: transparent;
          border-radius: 1px;
          transition: background var(--transition-fast);
        }

        &:hover { color: var(--text-primary); }
      }

      .active {
        color: var(--primary);
        font-weight: 500;
        &::after { background: var(--primary); }
      }
    }

    .login-form {
      padding: 28px 24px;
      background: #fff;
      border-radius: var(--radius-xl);
      border: 1px solid var(--border-color);
      box-shadow: var(--shadow-md);
      animation: fadeInUp 0.5s ease;

      .op-btn {
        width: 100%;
        transition: all 0.3s cubic-bezier(0.2,0,0,1);
        &:hover { transform: translateY(-1px); box-shadow: 0 4px 16px rgba(30,58,95,0.2); }
        &:active { transform: translateY(0); }
      }

      .email-code-row,
      .check-code-row {
        display: flex;
        width: 100%;
        gap: 8px;

        .el-input { flex: 1; }
        .send-btn { flex-shrink: 0; min-width: 110px; }
      }

      .check-code-img {
        height: 40px;
        border-radius: var(--radius-sm);
        cursor: pointer;
        flex-shrink: 0;
      }
    }
  }
}
</style>
