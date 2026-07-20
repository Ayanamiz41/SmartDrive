<template>
  <div class="audit-page">
    <h3 style="margin:0 0 10px 0">操作审计</h3>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input clearable placeholder="输入文件名搜索" v-model="filters.keyword" @keyup.enter="search" style="width:240px">
        <template #suffix>
          <i class="iconfont icon-search" @click="search"></i>
        </template>
      </el-input>
      <div style="position:relative; display:inline-block">
        <el-button :type="advFilterActive ? 'primary' : ''" style="margin-left:10px" @click="showAdvFilter = !showAdvFilter">高级筛选</el-button>
        <div v-show="showAdvFilter" class="adv-filter-panel" style="width:360px">
          <el-form label-width="70px" size="small" @submit.prevent>
            <el-form-item label="操作类型">
              <el-select v-model="filters.action" placeholder="全部" clearable style="width:100%">
                <el-option label="上传" value="UPLOAD" />
                <el-option label="下载" value="DOWNLOAD" />
                <el-option label="删除" value="DELETE" />
                <el-option label="重命名" value="RENAME" />
                <el-option label="移动" value="MOVE" />
                <el-option label="分享" value="SHARE" />
                <el-option label="取消分享" value="CANCEL_SHARE" />
                <el-option label="新建文件夹" value="CREATE_FOLDER" />
                <el-option label="恢复" value="RECOVER" />
                <el-option label="转存" value="SAVE_SHARE" />
                <el-option label="编辑摘要" value="EDIT_SUMMARY" />
                <el-option label="上传部门" value="COPY_TO_DEPT" />
                <el-option label="转存个人" value="COPY_TO_PERSONAL" />
              </el-select>
            </el-form-item>
            <el-form-item label="所属部门" v-if="userInfo.admin">
              <el-select v-model="filters.departmentId" placeholder="全部" clearable style="width:100%">
                <el-option v-for="d in deptList" :key="d.id" :label="d.name" :value="d.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="操作者" v-if="userInfo.admin || userInfo.deptHead">
              <el-select v-model="filters.userId" placeholder="输入姓名搜索" clearable filterable remote
                :remote-method="searchUsers" :loading="userLoading" style="width:100%">
                <el-option v-for="u in userList" :key="u.userId" :label="u.nickName" :value="u.userId" />
              </el-select>
            </el-form-item>
            <el-form-item label="操作时间">
              <el-date-picker v-model="filters.dateRange" type="daterange"
                range-separator="至" start-placeholder="开始" end-placeholder="结束"
                style="width:100%" />
            </el-form-item>
            <div style="text-align:right">
              <el-button size="small" @click="resetFilters">重置</el-button>
              <el-button size="small" type="primary" @click="search">查询</el-button>
            </div>
          </el-form>
        </div>
      </div>
      <span v-if="advFilterActive" class="exit-search" @click="resetFilters">清除筛选</span>
    </div>

    <!-- 表格 -->
    <div class="table-area">
    <el-table :data="tableData" border stripe v-loading="loading" style="margin-top:15px" :sort-orders="['descending', 'ascending', null]" @sort-change="handleSortChange">
      <el-table-column prop="userName" label="操作者" width="120" />
      <el-table-column label="操作类型" min-width="110">
        <template #default="{ row }">
          <el-tag :type="actionType(row.action)" style="white-space:nowrap">{{ actionLabel(row.action) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="目标文件" width="260" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.fileArchived" class="target-link-archived" @click="onTargetClick(row)">{{ row.targetName }}</span>
          <span v-else-if="row.fileDeleted" class="target-link-deleted" @click="onTargetClick(row)">{{ row.targetName }}</span>
          <span v-else class="target-link" @click="onTargetClick(row)">{{ row.targetName }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="departmentId" label="文件所属部门" width="140">
        <template #default="{ row }">
          {{ deptNameMap[row.departmentId] || row.departmentId || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="操作时间" width="180" sortable="custom" />
    </el-table>

    <el-pagination
      v-model:current-page="pageNo"
      :page-size="pageSize"
      :total="total"
      layout="prev, pager, next, total"
      @current-change="search"
      style="margin-top:8px; justify-content:center"
    />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from "vue";
import { getCurrentInstance } from "vue";
import { useRouter } from "vue-router";
const { proxy } = getCurrentInstance();
const router = useRouter();
const userInfo = proxy.VueCookies.get("userInfo") || {};

const loading = ref(false);
const tableData = ref([]);
const sortField = ref(null);
const sortOrder = ref(null);
const handleSortChange = ({ prop, order }) => {
  sortField.value = order ? prop : null;
  sortOrder.value = order;
  pageNo.value = 1;
  search();
};
const pageNo = ref(1);
const pageSize = ref(13);
const total = ref(0);
const deptList = ref([]);
const deptNameMap = reactive({});
const userList = ref([]);
const userLoading = ref(false);

const showAdvFilter = ref(false);
const filters = reactive({
  keyword: "",
  action: "",
  dateRange: null,
  departmentId: "",
  userId: "",
});

const advFilterActive = computed(() => {
  return !!(filters.action || filters.departmentId || filters.userId || (filters.dateRange && filters.dateRange.length === 2));
});

const actionMap = {
  UPLOAD: "上传", DOWNLOAD: "下载", DELETE: "删除", RENAME: "重命名",
  MOVE: "移动", SHARE: "分享", CANCEL_SHARE: "取消分享", CREATE_FOLDER: "新建文件夹",
  RECOVER: "恢复", SAVE_SHARE: "转存", EDIT_SUMMARY: "编辑摘要",
  ARCHIVE: "归档", UNARCHIVE: "取消归档",
  COPY_TO_DEPT: "上传部门", COPY_TO_PERSONAL: "转存个人",
};

const actionColor = {
  UPLOAD: "success", DOWNLOAD: "", DELETE: "danger", SHARE: "warning",
  RENAME: "info", MOVE: "", CANCEL_SHARE: "info", CREATE_FOLDER: "success",
  RECOVER: "info", SAVE_SHARE: "warning",
  COPY_TO_DEPT: "", COPY_TO_PERSONAL: "",
};

function actionLabel(action) { return actionMap[action] || action; }
function actionType(action) { return actionColor[action] || ""; }

function resetFilters() {
  showAdvFilter.value = false;
  filters.keyword = "";
  filters.action = "";
  filters.dateRange = null;
  filters.departmentId = "";
  filters.userId = "";
  pageNo.value = 1;
  search();
}

async function search() {
  showAdvFilter.value = false;
  loading.value = true;
  const params = {
    pageNo: pageNo.value,
    pageSize: pageSize.value,
    keyword: filters.keyword || undefined,
    action: filters.action || undefined,
    departmentId: filters.departmentId || undefined,
    userId: filters.userId || undefined,
  };
  if (sortField.value && sortOrder.value) {
    const dbField = sortField.value === 'createdAt' ? 'created_at' : sortField.value;
    params.orderBy = dbField + ' ' + (sortOrder.value === 'ascending' ? 'asc' : 'desc');
  }
  if (filters.dateRange && filters.dateRange.length === 2) {
    const toDateStr = (d) => `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
    params.startDate = toDateStr(filters.dateRange[0]);
    params.endDate = toDateStr(filters.dateRange[1]);
  }
  const res = await proxy.Request({ url: "/admin/audit/list", params });
  if (res && res.data) {
    tableData.value = res.data.list || [];
    total.value = res.data.totalCount || 0;
  }
  loading.value = false;
}

async function loadDeptList() {
  const res = await proxy.Request({ url: "/admin/department/list" });
  if (res && res.data) {
    deptList.value = res.data;
    for (const d of res.data) deptNameMap[d.id] = d.name;
  }
}

async function searchUsers(query) {
  if (!query) { userList.value = []; return; }
  userLoading.value = true;
  let res;
  if (userInfo.admin) {
    res = await proxy.Request({ url: "/admin/loadUserList", params: { nickNameFuzzy: query, pageSize: 20 } });
  } else {
    res = await proxy.Request({ url: "/auth/deptMembers", params: { nickNameFuzzy: query } });
  }
  userList.value = res?.data?.list || res?.data || [];
  userLoading.value = false;
}

function onTargetClick(row) {
  if (!row.targetId) return;
  if (row.fileArchived) {
    proxy.Message.warning("文件已被归档");
    return;
  }
  if (row.fileDeleted) {
    proxy.Message.warning("文件已被删除");
    return;
  }
  if (!userInfo.departmentId) {
    proxy.Message.warning("无所属部门，无法跳转");
    return;
  }
  const q = { deptId: row.departmentId };
  if (row.filePid && row.filePid !== "0") q.path = row.filePid;
  router.push({ path: "/main/all", query: q });
}

onMounted(() => { loadDeptList(); search(); });
</script>

<style scoped>
.audit-page { padding: 15px 20px; }
.search-bar { display: flex; align-items: center; justify-content: center; margin-bottom: 5px; }
.table-area { display: flex; flex-direction: column; align-items: center; width: 100%; }
.table-area :deep(.el-table) { width: auto !important; }
.target-link { color: var(--primary); cursor: pointer; }
.target-link:hover { text-decoration: underline; }
.target-link-deleted { color: var(--text-tertiary); cursor: pointer; text-decoration: line-through; }
.target-link-archived { color: var(--text-secondary); cursor: pointer; font-style: italic; font-weight: bold; }
.adv-filter-panel {
  position: absolute; top: 100%; left: 0; z-index: 2000;
  background: #fff; border: 1px solid var(--border-color); border-radius: var(--radius-md);
  padding: 15px; box-shadow: var(--shadow-md);
}
</style>
