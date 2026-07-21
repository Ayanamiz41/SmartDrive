<template>
  <div class="pdf">
    <vue-pdf-embed
      ref="pdfRef"
      :source="source"
      class="vue-pdf-embed"
      width="850"
      :style="scaleFun"
    />
  </div>
</template>

<script setup>
import VuePdfEmbed from "vue-pdf-embed";
import { ref, reactive, getCurrentInstance, computed } from "vue";
const { proxy } = getCurrentInstance();

const props = defineProps({
  url: {
    type: String,
  },
});

const scaleFun = computed(() => {
  return `transform:scale(${state.scale})`;
});

const state = ref({
  pageNum: 1,
  numPages: 0,
});

const token = localStorage.getItem("accessToken");

const source = computed(() => ({
  url: "/api" + props.url,
  httpHeaders: token ? { Authorization: `Bearer ${token}` } : {},
}));
</script>

<style lang="scss" scoped>
.pdf {
  width: 100%;
}
</style>
