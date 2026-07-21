<template>
  <div class="music">
    <div class="body-content">
      <div class="cover">
        <img src="@/assets/music_cover.png" />
      </div>
      <div ref="playerRef" class="music-player"></div>
    </div>
  </div>
</template>

<script setup>
import APlayer from "APlayer";
import "APlayer/dist/APlayer.min.css";
import axios from "axios";
import { ref, onMounted, onUnmounted } from "vue";

const props = defineProps({ url: String, fileName: String });

const playerRef = ref();
let player = null;

onMounted(async () => {
  // 用 axios 带 JWT 下载文件 → blob，绕过原生 audio 不带 auth header 的问题
  const token = localStorage.getItem("accessToken");
  const res = await axios.get(`/api${props.url}`, {
    responseType: "blob",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  const blobUrl = URL.createObjectURL(res.data);

  player = new APlayer({
    container: playerRef.value,
    audio: {
      url: blobUrl,
      name: props.fileName,
      artist: "",
    },
  });
});

onUnmounted(() => {
  if (player) player.destroy();
});
</script>

<style lang="scss" scoped>
.music {
  display: flex; align-items: center; justify-content: center; width: 100%;
  .body-content {
    text-align: center; width: 80%;
    .cover { margin: 0px auto; width: 200px; text-align: center; img { width: 100%; } }
    .music-player { margin-top: 20px; }
  }
}
</style>
