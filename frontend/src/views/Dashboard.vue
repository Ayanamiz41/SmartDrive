<template>
  <div class="dashboard" v-loading="loading">
    <!-- 欢迎横幅 — 渐变背景 -->
    <div class="welcome-banner" style="animation: fadeInDown 0.5s ease">
      <div class="welcome-left">
        <h2 class="greeting">{{ greetingText }}</h2>
        <p class="sub-text">{{ currentDate }} · {{ currentWeekday }}</p>
      </div>
      <div class="welcome-right">
        <div class="quick-stat" v-for="stat in headerStats" :key="stat.label">
          <span class="stat-value">{{ stat.value }}</span>
          <span class="stat-label">{{ stat.label }}</span>
        </div>
      </div>
    </div>

    <!-- 统计卡片 — 每张卡独特彩色 accent -->
    <div class="stat-grid">
      <div class="stat-card accent-blue" style="animation: fadeInUp 0.4s 0.1s ease both">
        <div class="stat-card-accent"></div>
        <div class="stat-card-header">
          <span class="stat-card-icon"><span class="iconfont icon-cloude"></span></span>
          <span class="stat-card-title">存储空间</span>
        </div>
        <div class="stat-card-body">
          <span class="stat-card-value" ref="spaceVal">{{ animatedSpace }}</span>
          <span class="stat-card-unit">{{ useSpaceUnit }}</span>
        </div>
        <div class="stat-card-progress">
          <el-progress :percentage="spacePercent" :stroke-width="4" :color="'#3182ce'" :show-text="false" />
          <div class="progress-info">
            <span class="progress-label">已使用</span>
            <span class="progress-value">{{ useSpaceDetail }}</span>
          </div>
        </div>
      </div>

      <div class="stat-card accent-emerald" style="animation: fadeInUp 0.4s 0.15s ease both">
        <div class="stat-card-accent"></div>
        <div class="stat-card-header">
          <span class="stat-card-icon"><span class="iconfont icon-doc"></span></span>
          <span class="stat-card-title">文件总数</span>
        </div>
        <div class="stat-card-body">
          <span class="stat-card-value">{{ animatedFileCount }}</span>
        </div>
      </div>

      <div class="stat-card accent-amber" style="animation: fadeInUp 0.4s 0.2s ease both">
        <div class="stat-card-accent"></div>
        <div class="stat-card-header">
          <span class="stat-card-icon"><span class="iconfont icon-share"></span></span>
          <span class="stat-card-title">分享记录</span>
        </div>
        <div class="stat-card-body">
          <span class="stat-card-value">{{ animatedShareCount }}</span>
        </div>
      </div>

      <div class="stat-card accent-rose" style="animation: fadeInUp 0.4s 0.25s ease both">
        <div class="stat-card-accent"></div>
        <div class="stat-card-header">
          <span class="stat-card-icon"><span class="iconfont icon-del"></span></span>
          <span class="stat-card-title">回收站</span>
        </div>
        <div class="stat-card-body">
          <span class="stat-card-value">{{ animatedRecycleCount }}</span>
        </div>
      </div>

      <div v-if="userInfo.deptHead" class="stat-card accent-violet" style="animation: fadeInUp 0.4s 0.3s ease both">
        <div class="stat-card-accent"></div>
        <div class="stat-card-header">
          <span class="stat-card-icon"><span class="iconfont icon-clock"></span></span>
          <span class="stat-card-title">待审批</span>
        </div>
        <div class="stat-card-body">
          <span class="stat-card-value">{{ animatedApprovalCount }}</span>
        </div>
      </div>
    </div>

    <!-- 快捷入口 -->
    <div class="section-title">快捷入口</div>
    <div class="quick-grid">
      <div class="quick-item" v-for="item in quickEntries" :key="item.name" @click="navigateTo(item.path)">
        <div class="quick-icon" :style="{ background: item.bg }">
          <span :class="['iconfont', 'icon-' + item.icon]" :style="{ color: item.iconColor || '#fff' }"></span>
        </div>
        <span class="quick-name">{{ item.name }}</span>
      </div>
    </div>

    <!-- 最近文件 — 彩色文件类型图标（管理员不显示） -->
    <div v-if="!userInfo.admin" class="section-title">最近文件</div>
    <div v-if="!userInfo.admin && recentFiles.length > 0" class="recent-grid">
      <div class="recent-item" v-for="file in recentFiles" :key="file.fileId" @click="openFile(file)">
        <div class="recent-icon" :style="{ background: getFileColor(file.fileType) }">
          <span :class="['iconfont', 'icon-' + getFileIcon(file.fileType)]"></span>
        </div>
        <div class="recent-info">
          <span class="recent-name">
            <span class="recent-name-body">{{ trimExt(file.fileName).name }}</span>
            <span class="recent-name-ext" v-if="trimExt(file.fileName).ext">.{{ trimExt(file.fileName).ext }}</span>
          </span>
          <span class="recent-meta">{{ formatTime(file.lastUpdateTime) }} · {{ formatSize(file.fileSize) }}</span>
        </div>
      </div>
    </div>
    <div v-else-if="!userInfo.admin" class="recent-empty">暂无最近文件</div>

    <Preview ref="previewRef"></Preview>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from "vue";
import { useRouter } from "vue-router";
import { getCurrentInstance } from "vue";
import Preview from "@/components/preview/Preview.vue";

const { proxy } = getCurrentInstance();
const router = useRouter();
const loading = ref(true);

const userInfo = proxy.VueCookies.get("userInfo") || {};
const userName = userInfo.nickName || "用户";

const greetingText = computed(() => {
  const hour = new Date().getHours();
  if (hour < 12) return `早上好，${userName}`;
  if (hour < 14) return `中午好，${userName}`;
  if (hour < 18) return `下午好，${userName}`;
  return `晚上好，${userName}`;
});
const currentDate = computed(() => `${new Date().getFullYear()}年${new Date().getMonth() + 1}月${new Date().getDate()}日`);
const currentWeekday = computed(() => ["星期日","星期一","星期二","星期三","星期四","星期五","星期六"][new Date().getDay()]);

// 存储空间
const useSpaceInfo = ref({ useSpace: 0, totalSpace: 1 });
const spacePercent = computed(() => useSpaceInfo.value.totalSpace ? Math.floor((useSpaceInfo.value.useSpace / useSpaceInfo.value.totalSpace) * 10000) / 100 : 0);
const useSpaceStr = computed(() => useSpaceInfo.value.useSpace >= 1073741824 ? (useSpaceInfo.value.useSpace / 1073741824).toFixed(1) : useSpaceInfo.value.useSpace >= 1048576 ? Math.round(useSpaceInfo.value.useSpace / 1048576) + "" : "0");
const useSpaceUnit = computed(() => useSpaceInfo.value.useSpace >= 1073741824 ? "GB" : "MB");
const useSpaceDetail = computed(() => proxy.Utils.size2Str(useSpaceInfo.value.useSpace) + " / " + proxy.Utils.size2Str(useSpaceInfo.value.totalSpace));

// 数字动画
const animatedSpace = ref("0");
const animatedFileCount = ref("0");
const animatedShareCount = ref("0");
const animatedRecycleCount = ref("0");
const animatedApprovalCount = ref("0");

function animateNumber(refKey, target) {
  const num = parseFloat(String(target).replace(/,/g, "")) || 0;
  const isFloat = String(target).includes(".");
  const start = 0;
  const duration = 800;
  const startTime = performance.now();
  function tick(now) {
    const elapsed = now - startTime;
    const progress = Math.min(elapsed / duration, 1);
    const eased = 1 - Math.pow(1 - progress, 3); // ease-out cubic
    const current = start + (num - start) * eased;
    if (isFloat) {
      animatedSpace.value = current.toFixed(1);
    } else {
      const keyMap = { space: animatedSpace, fileCount: animatedFileCount, shareCount: animatedShareCount, recycleCount: animatedRecycleCount, approvalCount: animatedApprovalCount };
      keyMap[refKey].value = Math.floor(current).toLocaleString();
    }
    if (progress < 1) requestAnimationFrame(tick);
  }
  requestAnimationFrame(tick);
}

const fileCount = ref("--"), shareCount = ref("--"), recycleCount = ref("--"), approvalCount = ref("--");
const headerStats = computed(() => [
  { label: "文件总数", value: fileCount.value },
  { label: "分享记录", value: shareCount.value },
  { label: "待清理", value: recycleCount.value },
]);

const recentFiles = ref([]);

// 文件类型 → icon（仅使用 iconfont 中实际存在的图标）
const fileTypeIconMap = { 0:"all",1:"video",2:"music",3:"image",4:"doc",5:"doc",6:"doc",7:"doc",8:"doc",9:"more",10:"more" };
const fileTypeColorMap = { 0:"#fef3c7",1:"#dbeafe",2:"#fce7f3",3:"#d1fae5",4:"#fee2e2",5:"#dbeafe",6:"#d1fae5",7:"#f3f4f6",8:"#e0e7ff",9:"#fef3c7",10:"#f3f4f6" };
const getFileIcon = (t) => fileTypeIconMap[t] || "more";
const getFileColor = (t) => fileTypeColorMap[t] || "#f3f4f6";

const trimExt = (name) => {
  const i = name.lastIndexOf(".");
  return i > 0 ? { name: name.slice(0, i), ext: name.slice(i + 1) } : { name, ext: "" };
};

const formatTime = (ts) => {
  if (!ts) return "";
  const t = typeof ts === "number" ? ts : new Date(ts).getTime(), diff = Date.now() - t;
  if (diff < 60000) return "刚刚";
  if (diff < 3600000) return Math.floor(diff / 60000) + " 分钟前";
  if (diff < 86400000) return Math.floor(diff / 3600000) + " 小时前";
  if (diff < 172800000) return "昨天";
  return `${new Date(t).getMonth() + 1}月${new Date(t).getDate()}日`;
};
const formatSize = (b) => proxy.Utils.size2Str(b || 0);

const allQuickEntries = [
  { name:"全部文件", icon:"all", path:"/main/all", bg:"#1e3a5f", allShow:true },
  { name:"我的分享", icon:"share", path:"/myshare", bg:"#2c5282", allShow:true, needDeptHead:true },
  { name:"回收站", icon:"del", path:"/recycle", bg:"#2b6cb0", allShow:true },
  { name:"部门管理", icon:"account", path:"/organization", bg:"#3182ce", allShow:true },
  { name:"归档库", icon:"import", path:"/archive", bg:"#4299e1", allShow:true },
  { name:"操作审计", icon:"clock", path:"/audit", bg:"#63b3ed", allShow:true },
  { name:"审批管理", icon:"doc", path:"/approval", bg:"#90cdf4", iconColor:"#1a365d", allShow:true },
  { name:"系统设置", icon:"settings", path:"/settings/sysSetting", bg:"#bee3f8", iconColor:"#1a365d", allShow:false },
];

const quickEntries = computed(() => {
  const admin = userInfo?.admin, deptHead = userInfo?.deptHead;
  return allQuickEntries.filter(item => {
    if (!item.allShow && !admin) return false;
    if (admin && (item.name === "我的分享" || item.name === "回收站" || item.name === "审批管理")) return false;
    if (item.needDeptHead && !deptHead && !admin) return false;
    return true;
  });
});

const navigateTo = (p) => { if (p) router.push(p); };

const previewRef = ref();
const openFile = (f) => {
  if (f.folderType === 1) { router.push({ path: "/main/all", query: { path: f.fileId } }); return; }
  if (f.status !== 2) { proxy.Message.warning("文件正在转码中，无法预览"); return; }
  previewRef.value.showPreview(f, 0);
};

onMounted(async () => {
  await Promise.all([
    (async () => { const r = await proxy.Request({ url:"/auth/getUseSpace", showLoading:false, showError:false }); if (r?.data) { useSpaceInfo.value = r.data; nextTick(() => animateNumber("space", useSpaceStr.value)); } })(),
    (async () => { const r = await proxy.Request({ url:"/file/loadDataList", params:{ pageNo:1, pageSize:1, category:"all" }, showLoading:false, showError:false }); if (r?.data) { fileCount.value = (r.data.totalCount||0).toLocaleString(); nextTick(() => animateNumber("fileCount", fileCount.value)); } })(),
    (async () => { const r = await proxy.Request({ url:"/share/loadShareList", params:{ pageNo:1, pageSize:1 }, showLoading:false, showError:false }); if (r?.data) { shareCount.value = (r.data.totalCount||0).toLocaleString(); nextTick(() => animateNumber("shareCount", shareCount.value)); } })(),
    (async () => { const r = await proxy.Request({ url:"/recycle/loadRecycleList", params:{ pageNo:1, pageSize:1 }, showLoading:false, showError:false }); if (r?.data) { recycleCount.value = (r.data.totalCount||0).toLocaleString(); nextTick(() => animateNumber("recycleCount", recycleCount.value)); } })(),
    (async () => { if (userInfo.deptHead) { const r = await proxy.Request({ url:"/approval/list", params:{ status:0, pageNo:1, pageSize:1 }, showLoading:false, showError:false }); if (r?.data) { approvalCount.value = (r.data.totalCount||0).toLocaleString(); nextTick(() => animateNumber("approvalCount", approvalCount.value)); } } })(),
    (async () => { if (!userInfo.admin) { const r = await proxy.Request({ url:"/file/loadDataList", params:{ pageNo:1, pageSize:6, category:"all", orderBy:"last_update_time desc" }, showLoading:false, showError:false }); if (r?.data?.list) recentFiles.value = r.data.list; } })(),
  ]);
  loading.value = false;
});
</script>

<style lang="scss" scoped>
.dashboard { max-width: 1200px; margin: 0 auto; min-height: 300px; animation: fadeIn .4s ease; }

@keyframes fadeIn { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }

/* 欢迎横幅 — 渐变背景 */
.welcome-banner {
  background: linear-gradient(135deg, #1e3a5f 0%, #2c5282 50%, #3182ce 100%);
  border-radius: var(--radius-lg);
  padding: 28px 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  color: #fff;
  .greeting { font-size: 22px; font-weight: 600; margin: 0 0 6px; color: #fff; }
  .sub-text { font-size: 13px; opacity: 0.8; margin: 0; }
  .welcome-right { display: flex; gap: 36px; }
  .quick-stat { text-align: center;
    .stat-value { display: block; font-size: 24px; font-weight: 400; line-height: 1.2; color: #fff; }
    .stat-label { display: block; font-size: 12px; opacity: 0.75; margin-top: 2px; }
  }
}

/* 统计卡片 — 彩色 accent 顶条 */
.stat-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 16px; margin-bottom: 24px; }

.stat-card {
  background: #fff; border: 1px solid var(--border-color); border-radius: var(--radius-lg);
  padding: 20px; box-shadow: var(--shadow-sm); transition: box-shadow var(--transition-fast);
  position: relative; overflow: hidden;
  &:hover { box-shadow: 0 4px 20px rgba(0,0,0,0.10); }
  .stat-card-accent { position: absolute; top: 0; left: 0; right: 0; height: 3px; border-radius: 3px 3px 0 0; }
  &.accent-blue .stat-card-accent { background: linear-gradient(90deg, #3182ce, #63b3ed); }
  &.accent-emerald .stat-card-accent { background: linear-gradient(90deg, #059669, #34d399); }
  &.accent-amber .stat-card-accent { background: linear-gradient(90deg, #d97706, #fbbf24); }
  &.accent-rose .stat-card-accent { background: linear-gradient(90deg, #e11d48, #fb7185); }
  &.accent-violet .stat-card-accent { background: linear-gradient(90deg, #7c3aed, #a78bfa); }
  .stat-card-header { display: flex; align-items: center; gap: 8px; margin: 4px 0 14px;
    .stat-card-icon .iconfont { font-size: 18px; }
    .stat-card-title { font-size: 13px; color: var(--text-tertiary); }
  }
  &.accent-blue .stat-card-icon .iconfont { color: #3182ce; }
  &.accent-emerald .stat-card-icon .iconfont { color: #059669; }
  &.accent-amber .stat-card-icon .iconfont { color: #d97706; }
  &.accent-rose .stat-card-icon .iconfont { color: #e11d48; }
  &.accent-violet .stat-card-icon .iconfont { color: #7c3aed; }
  .stat-card-body { display: flex; align-items: baseline; gap: 4px; margin-bottom: 10px;
    .stat-card-value { font-size: 24px; font-weight: 400; color: var(--text-primary); }
    .stat-card-unit { font-size: 14px; color: var(--text-tertiary); }
  }
  .stat-card-progress .progress-info { display: flex; justify-content: space-between; margin-top: 6px; font-size: 12px;
    .progress-label { color: var(--text-tertiary); }
    .progress-value { color: var(--text-secondary); }
  }
}

.section-title { font-size: 15px; font-weight: 600; color: var(--text-primary); margin-bottom: 14px; }

.quick-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 28px; }
.quick-item {
  background: #fff; border: 1px solid var(--border-color); border-radius: var(--radius-md);
  padding: 20px 16px; display: flex; align-items: center; gap: 12px;
  cursor: pointer; box-shadow: var(--shadow-sm); transition: box-shadow var(--transition-fast);
  &:hover { box-shadow: 0 4px 20px rgba(0,0,0,0.10); }
  .quick-icon { width: 40px; height: 40px; border-radius: var(--radius-sm); display: flex; align-items: center; justify-content: center; flex-shrink: 0;
    .iconfont { font-size: 20px; }
  }
  .quick-name { font-size: 14px; color: var(--text-primary); font-weight: 500; }
}

.recent-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.recent-item {
  background: #fff; border: 1px solid var(--border-color); border-radius: var(--radius-md);
  padding: 14px 16px; display: flex; align-items: center; gap: 12px; height: 60px;
  cursor: pointer; transition: box-shadow var(--transition-fast); overflow: hidden;
  &:hover { box-shadow: 0 4px 20px rgba(0,0,0,0.10); }
  .recent-icon { width: 36px; height: 36px; border-radius: var(--radius-sm); display: flex; align-items: center; justify-content: center; flex-shrink: 0;
    .iconfont { font-size: 18px; color: var(--text-secondary); }
  }
  .recent-info { flex: 1; min-width: 0;
    .recent-name {
      display: flex; overflow: hidden; font-size: 13px; color: var(--text-primary);
      .recent-name-body { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex-shrink: 1; min-width: 0; }
      .recent-name-ext { flex-shrink: 0; white-space: nowrap; }
    }
    .recent-meta { display: block; font-size: 12px; color: var(--text-tertiary); margin-top: 2px; }
  }
}

.recent-empty { text-align: center; color: var(--text-tertiary); font-size: 14px; padding: 40px 0; background: #fff; border: 1px solid var(--border-color); border-radius: var(--radius-md); }
</style>
