<template>
  <div class="framework">
    <!-- 顶部导航 -->
    <div class="header">
      <span class="menu-toggle" @click="sidebarOpen = !sidebarOpen">☰</span>
      <div class="logo">
        <span class="iconfont icon-pan"></span>
        <span class="name">知识资产平台</span>
      </div>
      <div class="right-panel">
        <el-popover
          :width="800"
          trigger="click"
          v-model:visible="showUploader"
          :offset="20"
          transition="none"
          :hide-after="0"
          :popper-style="{ padding: '0px' }"
        >
          <template #reference>
            <span class="iconfont icon-transfer"></span>
          </template>
          <template #default>
            <Uploader ref="uploaderRef" @uploadCallback="uploadCallbackHandler"></Uploader>
          </template>
        </el-popover>

        <span v-if="userInfo.deptHead" class="iconfont icon-notice" @click="$router.push('/approval')" style="cursor:pointer;font-size:22px;color:var(--text-secondary);margin-right:14px" title="审批管理"></span>

        <el-dropdown>
          <div class="user-info">
            <div class="avatar">
              <Avatar v-if="userInfo?.userId" :userId="userInfo.userId" :avatar="userInfo.avatar" :timestamp="timestamp" :width="34"></Avatar>
            </div>
            <span class="nick-name">{{ userInfo?.nickName }}</span>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="updateAvatar">修改头像</el-dropdown-item>
              <el-dropdown-item @click="updatePassword">修改密码</el-dropdown-item>
              <el-dropdown-item @click="logout">退出</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <!-- 主体 -->
    <div class="body">
      <div class="sidebar-overlay" v-show="sidebarOpen" @click="sidebarOpen = false"></div>
      <div class="left-sider" :class="{ collapsed: !hasSubMenu, open: sidebarOpen }">
        <div class="menu-list">
          <template v-for="(item, idx) in visibleMenus" :key="idx">
            <div v-if="item._groupLabel" class="menu-group-label" @click="toggleGroup(item._groupLabel)">
              <span class="group-arrow" :class="{ collapsed: collapsedGroups[item._groupLabel] }">▸</span>
              {{ item._groupLabel }}
            </div>
            <div v-else-if="!collapsedGroups[item._groupRef]"
              @click="jump(item)"
              :class="['menu-item', item.menuCode === currentMenu?.menuCode ? 'active' : '']"
            >
              <div :class="['iconfont', 'icon-' + item.icon]"></div>
              <div class="text">{{ item.name }}</div>
            </div>
          </template>
        </div>

        <div class="menu-sub-list" v-if="hasSubMenu">
          <div
            @click="jump(sub)"
            :class="['menu-item-sub', currentPath === sub.path ? 'active' : '']"
            v-for="(sub, si) in currentMenu.children" :key="si"
          >
            <span :class="['iconfont', 'icon-' + sub.icon]" v-if="sub.icon"></span>
            <span class="text">{{ sub.name }}</span>
          </div>
          <div class="tips" v-if="currentMenu?.tips">{{ currentMenu.tips }}</div>
          <div class="space-info">
            <div class="label">空间使用</div>
            <div class="percent">
              <el-progress
                :percentage="Math.floor((useSpaceInfo.useSpace / useSpaceInfo.totalSpace) * 10000) / 100"
                :stroke-width="4" color="#1e3a5f"
              />
            </div>
            <div class="space-use">
              <div class="use">{{ proxy.Utils.size2Str(useSpaceInfo.useSpace) }}/{{ proxy.Utils.size2Str(useSpaceInfo.totalSpace) }}</div>
              <div class="iconfont icon-refresh" @click="getUseSpace"></div>
            </div>
          </div>
        </div>
      </div>

      <div class="body-content">
        <router-view v-if="userInfoReady" v-slot="{ Component }">
          <component @addFile="addFile" ref="routerViewRef" :is="Component" @reload="getUseSpace" />
        </router-view>
      </div>
    </div>

    <UpdateAvatar ref="updateAvatarRef" @updateAvatar="reloadAvatar"></UpdateAvatar>
    <UpdatePassword ref="updatePasswordRef"></UpdatePassword>
  </div>
</template>

<script setup>
import UpdateAvatar from "./UpdateAvatar.vue";
import UpdatePassword from "./UpdatePassword.vue";
import Uploader from "@/views/main/Uploader.vue";
import { ref, reactive, getCurrentInstance, watch, nextTick, computed, onBeforeUnmount } from "vue";
import { useRouter, useRoute } from "vue-router";
import { ElNotification } from "element-plus";

const { proxy } = getCurrentInstance();
const router = useRouter();
const route = useRoute();

const showUploader = ref(false);
const uploaderRef = ref();
const addFile = (data) => { showUploader.value = true; uploaderRef.value.addFile(data.file, data.filePid, data.deptMode, data.departmentId, data.summary); };
const routerViewRef = ref();
const uploadCallbackHandler = () => { nextTick(() => { routerViewRef.value.reload(); getUseSpace(); }); };

const timestamp = ref(0);
const userInfo = ref(proxy.VueCookies.get("userInfo"));
const userInfoReady = ref(false);
(async () => {
  const res = await proxy.Request({ url: "/auth/getUserInfo", showLoading: false, showError: false });
  if (res?.data?.userId) { userInfo.value = Object.assign({}, userInfo.value, res.data); proxy.VueCookies.set("userInfo", userInfo.value, 0); }
  userInfoReady.value = true;
})();

const menus = [
  { icon: "all", name: "仪表盘", menuCode: "dashboard", path: "/dashboard", allShow: true, group: "我的", children: [] },
  { icon: "cloude", name: "首页", menuCode: "main", path: "/main/all", allShow: true, group: "我的", children: [
    { icon: "all", name: "全部", category: "all", path: "/main/all" },
    { icon: "video", name: "视频", category: "video", path: "/main/video" },
    { icon: "music", name: "音频", category: "music", path: "/main/music" },
    { icon: "image", name: "图片", category: "image", path: "/main/image" },
    { icon: "doc", name: "文档", category: "doc", path: "/main/doc" },
    { icon: "more", name: "其他", category: "others", path: "/main/others" },
  ]},
  { path: "/myshare", icon: "share", name: "分享", menuCode: "share", allShow: true, group: "我的", children: [{ name: "分享记录", path: "/myshare" }] },
  { path: "/recycle", icon: "del", name: "回收站", menuCode: "recycle", tips: "回收站为你保存10天内删除的文件", allShow: true, group: "我的", children: [{ name: "删除的文件", path: "/recycle" }] },
  { path: "/organization", icon: "account", name: "部门管理", menuCode: "organization", allShow: true, group: "团队", children: [] },
  { path: "/audit", icon: "clock", name: "操作审计", menuCode: "audit", allShow: true, group: "团队", children: [] },
  { path: "/approval", icon: "doc", name: "审批管理", menuCode: "approval", allShow: true, group: "团队", children: [] },
  { path: "/archive", icon: "import", name: "归档库", menuCode: "archive", allShow: true, group: "团队", children: [] },
  { path: "/settings/userList", icon: "settings", name: "设置", menuCode: "settings", allShow: false, group: "管理", children: [
    { name: "用户管理", path: "/settings/userList" },
    { path: "/settings/sysSetting", name: "系统设置" },
  ]},
];

// 可见菜单（插入分组标签）
const visibleMenus = computed(() => {
  const result = [];
  let lastGroup = null;
  for (const item of menus) {
    const admin = userInfo.value?.admin, deptHead = userInfo.value?.deptHead;
    if (!item.allShow && !admin) continue;
    if (admin && (item.menuCode === "share" || item.menuCode === "recycle" || item.menuCode === "approval")) continue;
    if (item.menuCode === "share" && !deptHead) continue;
    if (item.group && item.group !== lastGroup) {
      result.push({ _groupLabel: item.group });
      lastGroup = item.group;
    }
    result.push({ ...item, _groupRef: lastGroup });
  }
  return result;
});

// 侧边栏分组折叠状态
const collapsedGroups = reactive({});

const toggleGroup = (groupName) => {
  collapsedGroups[groupName] = !collapsedGroups[groupName];
};

const currentMenu = ref({});
const currentPath = ref();
const sidebarOpen = ref(false);
const hasSubMenu = computed(() => currentMenu.value?.children?.length > 0);

// 路由变化时关闭侧边栏，解除 body 滚动锁
watch(() => route.path, () => { sidebarOpen.value = false; document.body.style.overflow = ''; });
watch(sidebarOpen, (v) => { document.body.style.overflow = v ? 'hidden' : ''; });

const jump = (data) => {
  if (!data.path || data.menuCode === currentMenu.value?.menuCode) return;
  router.push(data.path);
};

const setMenu = (menuCode, path) => {
  const menu = menus.find((item) => item.menuCode === menuCode);
  currentMenu.value = menu;
  currentPath.value = path;
  // 自动展开当前菜单所在分组
  if (menu?.group && collapsedGroups[menu.group]) {
    collapsedGroups[menu.group] = false;
  }
};

watch(() => route, (v) => { if (v.meta.menuCode) setMenu(v.meta.menuCode, v.path); }, { immediate: true, deep: true });

const updateAvatarRef = ref();
const updateAvatar = () => updateAvatarRef.value.show(userInfo.value);
const reloadAvatar = () => { userInfo.value = proxy.VueCookies.get("userInfo"); timestamp.value = Date.now(); };

const updatePasswordRef = ref();
const updatePassword = () => updatePasswordRef.value.show();

const logout = () => {
  proxy.Confirm("确定要退出登录吗？", async () => {
    if (!await proxy.Request({ url: "/auth/logout" })) return;
    proxy.VueCookies.remove("userInfo");
    localStorage.removeItem("accessToken"); localStorage.removeItem("refreshToken");
    router.push("/login");
  });
};

const useSpaceInfo = ref({ useSpace: 0, totalSpace: 1 });
const getUseSpace = async () => { const r = await proxy.Request({ url: "/auth/getUseSpace", showLoading: false }); if (r?.data) useSpaceInfo.value = r.data; };
getUseSpace();

const pendingApprovalCount = ref(0);
let prevApprovalCount = -1; // -1 表示首次加载，不弹窗
const fetchApprovalCount = async () => {
  if (!userInfo.value?.deptHead) return;
  const r = await proxy.Request({ url: "/approval/pending-count", showLoading: false, showError: false });
  if (r?.data?.count === undefined) return;
  const cur = r.data.count;
  if (prevApprovalCount !== -1 && cur > prevApprovalCount) {
    const diff = cur - prevApprovalCount;
    ElNotification({
      title: "审批提醒",
      message: `您有 ${diff} 条新的待审批请求`,
      type: "info",
      duration: 5000,
      position: "top-right",
      onClick: () => router.push("/approval"),
    });
  }
  prevApprovalCount = cur;
  pendingApprovalCount.value = cur;
};
fetchApprovalCount();
const approvalPollTimer = setInterval(fetchApprovalCount, 30000);
onBeforeUnmount(() => clearInterval(approvalPollTimer));
</script>

<style lang="scss" scoped>
.header {
  height: 52px; padding: 0 20px; display: flex; align-items: center; justify-content: space-between;
  background: #fff; border-bottom: 1px solid var(--border-color); z-index: 200; position: relative;
  &::after { content: ''; position: absolute; bottom: 0; left: 0; right: 0; height: 1px; background: linear-gradient(90deg, #1e3a5f, #3182ce, #63b3ed, transparent); }
  .logo {
    display: flex; align-items: center;
    .icon-pan { font-size: 22px; color: #fff; background: var(--primary); width: 36px; height: 36px; display: flex; align-items: center; justify-content: center; border-radius: var(--radius-sm); }
    .name { font-weight: 600; margin-left: 10px; font-size: 18px; color: var(--text-primary); letter-spacing: .5px; }
  }
  .right-panel {
    display: flex; align-items: center;
    .icon-transfer { cursor: pointer; font-size: 22px; color: var(--text-secondary); transition: color var(--transition-fast); &:hover { color: var(--text-primary); } }
    .user-info {
      margin-left: 16px; display: flex; align-items: center; cursor: pointer; padding: 4px 8px; border-radius: var(--radius-md); transition: background var(--transition-fast);
      &:hover { background: var(--bg-hover); }
      .avatar { margin-right: 8px; flex-shrink: 0; }
      .nick-name { color: var(--text-secondary); font-size: 14px; }
    }
  }
}

.body {
  display: flex;
  .left-sider {
    border-right: 1px solid var(--border-color); display: flex; background: #fff;
    &.collapsed .menu-list { border-right: none; }
    .menu-list {
      height: calc(100vh - 52px); width: 64px; padding: 8px 4px 0; background: #F8FAFC; border-right: 1px solid var(--border-color); overflow-y: auto;
      .menu-group-label {
        font-size: 11px; color: var(--text-tertiary); padding: 12px 4px 4px; letter-spacing: 1px; text-align: center;
        cursor: pointer; user-select: none;
        &:hover { color: var(--text-secondary); }
        .group-arrow {
          display: inline-block; font-size: 10px; margin-right: 1px;
          transition: transform var(--transition-fast);
          transform: rotate(90deg);
          &.collapsed { transform: rotate(0deg); }
        }
      }
      .menu-item {
        text-align: center; font-size: 12px; padding: 10px 4px; cursor: pointer; border-radius: var(--radius-sm); margin-bottom: 2px; position: relative; transition: all var(--transition-fast);
        &:hover { background: #e8eef4; transform: translateX(2px); }
        .iconfont { font-size: 22px; display: block; color: var(--text-secondary); transition: color var(--transition-fast); }
        .text { margin-top: 3px; color: var(--text-tertiary); font-size: 11px; }
        &::before { content: ''; position: absolute; left: 0; top: 4px; bottom: 4px; width: 2px; background: transparent; border-radius: 1px; transition: background var(--transition-fast); }
      }
      .active {
        background: #dce8f2;
        .iconfont, .text { color: var(--primary); }
        .text { font-weight: 500; }
        &::before { background: linear-gradient(180deg, #3182ce, #1e3a5f); }
      }
    }
    .menu-sub-list {
      width: 192px; padding: 12px 8px; position: relative; overflow-y: auto;
      .menu-item-sub {
        line-height: 34px; border-radius: var(--radius-sm); cursor: pointer; padding: 0 10px; margin-bottom: 1px; position: relative; transition: background var(--transition-fast);
        &:hover { background: var(--bg-hover); }
        .iconfont { font-size: 14px; margin-right: 10px; color: var(--text-secondary); }
        .text { font-size: 13px; color: var(--text-primary); }
      }
      .active {
        background: var(--bg-active);
        .iconfont, .text { color: var(--primary); }
        .text { font-weight: 500; }
        &::before { content: ''; position: absolute; left: 0; top: 4px; bottom: 4px; width: 2px; background: linear-gradient(180deg, #3182ce, #1e3a5f); border-radius: 1px; }
      }
      .tips { margin-top: 8px; color: var(--text-tertiary); font-size: 12px; padding: 0 10px; }
      .space-info {
        position: absolute; bottom: 10px; left: 8px; right: 8px;
        .label { font-size: 12px; color: var(--text-tertiary); margin-bottom: 6px; }
        .percent { margin-bottom: 4px; }
        .space-use { display: flex; align-items: center; justify-content: space-between; font-size: 12px; color: var(--text-tertiary);
          .iconfont { cursor: pointer; color: var(--text-secondary); font-size: 13px; transition: color var(--transition-fast); &:hover { color: var(--primary); } }
        }
      }
    }
  }
  .body-content { flex: 1; width: 0; padding: 20px; overflow-y: auto; background: var(--bg-page); animation: pageIn .3s ease; }
}

@keyframes pageIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }

/* 响应式：≤800px 侧边栏折叠 */
.menu-toggle { display: none; }
.sidebar-overlay { display: none; }

@media (max-width: 800px) {
  .menu-toggle { display: inline-block; font-size: 24px; cursor: pointer; margin-right: 10px; color: var(--text-secondary); user-select: none; }
  .sidebar-overlay { display: block; position: fixed; inset: 0; background: rgba(0,0,0,0.3); z-index: 250; }
  .body .left-sider { position: fixed; left: 0; top: 52px; bottom: 0; z-index: 300; transform: translateX(-100%); transition: transform 200ms ease; }
  .body .left-sider.open { transform: translateX(0); }
  .body-content { padding: 12px; }
}
</style>
