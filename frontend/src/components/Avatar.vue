<template>
  <span class="avatar" :style="{ width: width + 'px', height: width + 'px' }">
    <img
      v-if="userId && !imgError"
      :src="avatar && avatar != '' ? avatar : `${proxy.globalInfo.avatarUrl}${userId}?${timestamp}`"
      @error="imgError = true"
    />
    <span v-else-if="userId" class="avatar-fallback" :style="{ width: width + 'px', height: width + 'px', fontSize: (width * 0.4) + 'px', lineHeight: width + 'px' }">
      {{ initial }}
    </span>
  </span>
</template>

<script setup>
import { ref, computed, getCurrentInstance } from "vue";
const { proxy } = getCurrentInstance();

const props = defineProps({
  userId: { type: String },
  avatar: { type: String },
  timestamp: { type: Number, default: 0 },
  width: { type: Number, default: 40 },
});

const imgError = ref(false);

const initial = computed(() => {
  // 从 cookie 中取昵称首字符
  const info = proxy.VueCookies.get("userInfo");
  const name = info?.nickName || props.userId || "?";
  return name.charAt(0).toUpperCase();
});
</script>

<style lang="scss" scoped>
.avatar {
  display: inline-flex;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}
.avatar-fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--primary);
  color: #fff;
  font-weight: 500;
  user-select: none;
}
</style>
