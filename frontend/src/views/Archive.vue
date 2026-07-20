<template>
  <div class="archive-page">
    <!-- 管理员：部门选择页 -->
    <template v-if="userInfo.admin && !selectedDept">
      <div class="dept-home">
        <h2 style="text-align:center;margin:30px 0">选择部门归档库</h2>
        <div class="dept-grid">
          <div v-for="d in deptList" :key="d.id" class="dept-card" @click="enterDept(d)">
            <span class="iconfont icon-team" style="font-size:48px;color:var(--primary)"></span>
            <div class="dept-name">{{ d.name }}</div>
          </div>
        </div>
      </div>
    </template>

    <!-- 归档文件列表 -->
    <template v-else>
      <h3 style="margin:0 0 10px 0">
        归档库
        <template v-if="selectedDept"> - {{ selectedDept.name }}</template>
        <template v-else-if="!userInfo.admin && userInfo.departmentId"> - {{ deptName }}</template>
      </h3>
      <el-button v-if="userInfo.admin && selectedDept" type="warning" size="small" @click="selectedDept=null; pageNo=1" style="margin-bottom:10px">返回部门列表</el-button>

      <!-- 面包屑 -->
      <div class="breadcrumb" v-if="breadcrumb.length > 0" style="margin-bottom:8px;font-size:14px">
        <span class="breadcrumb-item" @click="navToFolder(0)">根目录</span>
        <template v-for="(item, idx) in breadcrumb" :key="item.fileId">
          <span style="margin:0 4px;color:var(--text-tertiary)"> / </span>
          <span class="breadcrumb-item" @click="navToFolder(idx + 1)">{{ item.fileName }}</span>
        </template>
      </div>

      <!-- 搜索栏 -->
      <div class="search-bar" v-if="!userInfo.admin || selectedDept">
        <el-input clearable placeholder="输入文件名搜索" v-model="keyword" @keyup.enter="search" style="width:240px">
          <template #suffix>
            <i class="iconfont icon-search" @click="search"></i>
          </template>
        </el-input>
        <div style="position:relative; display:inline-block">
          <el-button :type="advFilterActive ? 'primary' : ''" style="margin-left:10px" @click="showAdvFilter = !showAdvFilter">高级筛选</el-button>
          <div v-show="showAdvFilter" class="adv-filter-panel">
            <el-form label-width="70px" size="small" @submit.prevent>
              <el-form-item label="上传者">
                <el-select v-model="advFilters.uploaderUserId" clearable filterable remote
                  :remote-method="searchUploaders" :loading="uploaderLoading" placeholder="输入姓名搜索" style="width:100%">
                  <el-option v-for="u in uploaderList" :key="u.userId" :label="u.nickName" :value="u.userId" />
                </el-select>
              </el-form-item>
              <el-form-item label="创建时间">
                <el-date-picker v-model="advFilters.createTimeRange" type="daterange"
                  range-separator="至" start-placeholder="开始" end-placeholder="结束"
                  style="width:100%" />
              </el-form-item>
              <el-form-item label="归档时间">
                <el-date-picker v-model="advFilters.archivedTimeRange" type="daterange"
                  range-separator="至" start-placeholder="开始" end-placeholder="结束"
                  style="width:100%" />
              </el-form-item>
              <div style="text-align:right">
                <el-button size="small" @click="resetSearch">重置</el-button>
                <el-button size="small" type="primary" @click="search">搜索</el-button>
              </div>
            </el-form>
          </div>
        </div>
        <span v-if="isSearchMode" class="exit-search" @click="resetSearch">退出搜索</span>
      </div>

      <div class="table-area">
        <el-table :data="tableData" border stripe v-loading="loading" style="margin-top:10px" :sort-orders="['descending', 'ascending', null]" @sort-change="handleSortChange">
          <el-table-column prop="fileName" label="文件名" min-width="260">
            <template #default="{ row }">
              <div>
                <div class="file-item-row">
                  <span class="summary-tag" v-if="row.folderType == 0" @click.stop="toggleSummary(row)">摘要</span>
                  <icon v-if="row.folderType == 0" :fileType="row.fileType" :width="24"></icon>
                  <icon v-if="row.folderType == 1" :fileType="0" :width="24"></icon>
                  <span class="file-name" :title="row.fileName" style="margin-left:6px" @click="clickRow(row)">{{ row.fileName }}</span>
                </div>
                <div class="summary-inline" v-if="row.summaryExpanded" @click.stop>
                  <span class="summary-inline-text">{{ row.summary }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="nickName" label="上传者" width="120" />
          <el-table-column label="大小" width="100" prop="fileSize" sortable="custom">
            <template #default="{ row }">
              <span v-if="row.fileSize">{{ proxy.Utils.size2Str(row.fileSize) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="170" sortable="custom" />
          <el-table-column prop="archivedTime" label="归档时间" width="170" sortable="custom" />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <span class="iconfont icon-download" @click="download(row)" title="下载" v-if="row.folderType == 0"></span>
              <span v-if="canUnarchive && breadcrumb.length === 0" class="iconfont icon-revert" @click="unarchiveFile(row)" title="取消归档" style="margin-left:8px;color:#e6a23c"></span>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="pageNo"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next, total"
          @current-change="doLoad"
          style="margin-top:8px; justify-content:center"
        />
      </div>
    </template>
    <Preview ref="previewRef"></Preview>
  </div>
</template>

<script setup>
import { ref, computed, getCurrentInstance, onMounted } from "vue";
const { proxy } = getCurrentInstance();
const userInfo = proxy.VueCookies.get("userInfo") || {};

const loading = ref(false);
const tableData = ref([]);
const sortField = ref(null);
const sortOrder = ref(null);
const handleSortChange = ({ prop, order }) => {
  sortField.value = order ? prop : null;
  sortOrder.value = order;
  pageNo.value = 1;
  doLoad();
};
const pageNo = ref(1);
const pageSize = ref(13);
const total = ref(0);
const currentFolder = ref({ fileId: 0 });
const breadcrumb = ref([]); // [{fileId, fileName}]

const deptList = ref([]);
const deptName = ref("");
const selectedDept = ref(null);

const canUnarchive = computed(() => userInfo.admin);

const previewRef = ref();

// === 搜索 ===
const showAdvFilter = ref(false);
const keyword = ref("");
const isSearchMode = ref(false);
const advFilters = ref({
  uploaderUserId: "",
  createTimeRange: null,
  archivedTimeRange: null,
});
const advFilterActive = computed(() => {
  const f = advFilters.value;
  return !!(f.uploaderUserId || (f.createTimeRange && f.createTimeRange.length === 2) || (f.archivedTimeRange && f.archivedTimeRange.length === 2));
});
const uploaderList = ref([]);
const uploaderLoading = ref(false);

const searchUploaders = async (query) => {
  if (!query) { uploaderList.value = []; return; }
  uploaderLoading.value = true;
  let res;
  if (userInfo.admin) {
    res = await proxy.Request({ url: "/admin/loadUserList", params: { nickNameFuzzy: query, pageSize: 20 }, showLoading: false });
  } else {
    const params = { nickNameFuzzy: query };
    if (selectedDept.value) params.departmentId = selectedDept.value.id;
    res = await proxy.Request({ url: "/auth/deptMembers", params, showLoading: false });
  }
  uploaderList.value = res?.data?.list || res?.data || [];
  uploaderLoading.value = false;
};

const resetSearch = () => {
  showAdvFilter.value = false;
  keyword.value = "";
  advFilters.value = { uploaderUserId: "", createTimeRange: null, archivedTimeRange: null };
  isSearchMode.value = false;
  pageNo.value = 1;
  doLoad();
};

const search = () => {
  showAdvFilter.value = false;
  isSearchMode.value = !!(keyword.value || advFilterActive.value);
  pageNo.value = 1;
  doLoad();
};

// === 数据加载 ===
function buildDeptId() {
  if (selectedDept.value) return selectedDept.value.id;
  if (!userInfo.admin) return userInfo.departmentId;
  return null;
}

async function doLoad() {
  loading.value = true;
  if (isSearchMode.value) {
    // ES 搜索
    const params = {
      pageNo: pageNo.value || 1,
      pageSize: pageSize.value || 15,
      keyword: keyword.value || "",
      archived: 1,
      deptMode: true,
    };
    const deptId = buildDeptId();
    if (deptId) params.departmentId = deptId;
    const f = advFilters.value;
    if (f.uploaderUserId) params.uploaderUserId = f.uploaderUserId;
    if (f.createTimeRange && f.createTimeRange.length === 2) {
      params.createTimeStart = f.createTimeRange[0].getTime();
      params.createTimeEnd = f.createTimeRange[1].getTime() + 86399999;
    }
    if (f.archivedTimeRange && f.archivedTimeRange.length === 2) {
      params.archivedTimeStart = f.archivedTimeRange[0].getTime();
      params.archivedTimeEnd = f.archivedTimeRange[1].getTime() + 86399999;
    }
    if (sortField.value && sortOrder.value) {
      params.sortField = sortField.value;
      params.sortOrder = sortOrder.value;
    }
    const res = await proxy.Request({ url: "/file/search", params, showLoading: false });
    if (res && res.data) {
      tableData.value = res.data.list || [];
      total.value = res.data.totalCount || 0;
    }
  } else {
    // MySQL 分页
    const SORT_MAP = { createTime: 'create_time', archivedTime: 'archived_time', fileSize: 'file_size' };
    const params = { pageNo: pageNo.value || 1, pageSize: pageSize.value || 15, filePid: currentFolder.value.fileId };
    if (sortField.value && sortOrder.value) {
      const dbField = SORT_MAP[sortField.value];
      if (dbField) params.orderBy = dbField + ' ' + (sortOrder.value === 'ascending' ? 'asc' : 'desc');
    }
    const deptId = buildDeptId();
    if (deptId) params.departmentId = deptId;
    const res = await proxy.Request({ url: "/file/loadArchiveList", params, showLoading: false });
    if (res && res.data) {
      tableData.value = res.data.list || [];
      total.value = res.data.totalCount || 0;
    }
  }
  loading.value = false;
}

// === 操作 ===
function clickRow(row) {
  if (row.folderType == 1) {
    // 进入文件夹
    breadcrumb.value.push({ fileId: row.fileId, fileName: row.fileName });
    currentFolder.value = { fileId: row.fileId };
    pageNo.value = 1;
    isSearchMode.value = false;
    keyword.value = "";
    doLoad();
  } else {
    if (row.status != 2) { proxy.Message.warning("文件正在转码中，无法预览"); return; }
    previewRef.value.showPreview(row, 0);
  }
}
function navToFolder(level) {
  breadcrumb.value = breadcrumb.value.slice(0, level);
  currentFolder.value = level === 0 ? { fileId: 0 } : { fileId: breadcrumb.value[level - 1].fileId };
  pageNo.value = 1;
  isSearchMode.value = false;
  keyword.value = "";
  doLoad();
}

// === 摘要 ===
function toggleSummary(row) {
  row.summaryExpanded = !row.summaryExpanded;
}

async function loadDeptList() {
  const res = await proxy.Request({ url: "/admin/department/list" });
  if (res && res.data) deptList.value = res.data;
}

async function loadDeptName() {
  const res = await proxy.Request({ url: "/admin/department/list" });
  if (res && res.data) {
    for (const d of res.data) {
      if (d.id === userInfo.departmentId) { deptName.value = d.name; break; }
    }
  }
}

function enterDept(dept) {
  selectedDept.value = dept;
  currentFolder.value = { fileId: 0 };
  breadcrumb.value = [];
  pageNo.value = 1;
  doLoad();
}

async function download(row) {
  let result = await proxy.Request({ url: "/file/createDownloadUrl/" + row.fileId });
  if (!result) return;
  window.location.href = "/api/file/download/" + result.data;
}

async function unarchiveFile(row) {
  proxy.Confirm(`确定要取消归档【${row.fileName}】吗？`, async () => {
    let result = await proxy.Request({ url: "/file/unarchive", params: { fileIds: row.fileId } });
    if (!result) return;
    proxy.Message.success("已取消归档");
    doLoad();
  });
}

onMounted(async () => {
  if (userInfo.admin) {
    await loadDeptList();
  } else {
    await loadDeptName();
    doLoad();
  }
});
</script>

<style scoped>
.archive-page { padding: 15px 20px; }
.search-bar { display: flex; align-items: center; justify-content: center; margin-bottom: 8px; }
.table-area { display: flex; flex-direction: column; align-items: center; width: 100%; }
.table-area :deep(.el-table) { width: auto !important; }
.file-item-row { display: flex; align-items: center; }
.file-item-row :deep(.icon) { flex-shrink: 0; }
.file-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; cursor: pointer; color: var(--text-secondary); transition: color var(--transition-fast); }
.file-name:hover { color: var(--primary); text-decoration: underline; }
.summary-tag {
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  margin-right: 8px;
  padding: 0 6px;
  border: 1px solid var(--border-color);
  border-radius: 3px;
  white-space: nowrap;
  flex-shrink: 0;
  transition: all var(--transition-fast);
}
.summary-tag:hover { background: var(--bg-hover); color: var(--primary); border-color: var(--primary); }
.summary-inline {
  padding: 8px 12px 8px 40px;
  background: var(--bg-hover);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: flex-start;
}
.summary-inline-text {
  flex: 1;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  word-break: break-all;
}
.iconfont { cursor: pointer; font-size: 16px; }
.icon-download { color: var(--text-secondary); }
.icon-revert { color: var(--warning); }
.dept-home { max-width:1000px; margin:0 auto; }
.dept-grid { display:flex; flex-wrap:wrap; gap:20px; justify-content:center; }
.dept-card { width:200px; padding:30px 20px; border-radius:var(--radius-lg); text-align:center; cursor:pointer; background:#fff; border:1px solid var(--border-color); transition: box-shadow var(--transition-fast); animation: fadeInUp 0.35s ease both; }
.dept-card:nth-child(1) { animation-delay: 0.05s; }
.dept-card:nth-child(2) { animation-delay: 0.1s; }
.dept-card:nth-child(3) { animation-delay: 0.15s; }
.dept-card:nth-child(4) { animation-delay: 0.2s; }
.dept-card:nth-child(5) { animation-delay: 0.25s; }
.dept-card:nth-child(6) { animation-delay: 0.3s; }
.dept-card:hover { border-color:var(--primary); box-shadow:var(--shadow-md); }
.dept-name { margin-top:12px; font-size:16px; font-weight:500; color: var(--text-primary); }
.adv-filter-panel {
  position: absolute; top: 100%; left: 0; z-index: 2000;
  background: #fff; border: 1px solid var(--border-color); border-radius: var(--radius-md);
  padding: 15px; box-shadow: var(--shadow-md); width: 340px;
}
.breadcrumb-item { color: var(--text-secondary); cursor: pointer; transition: color var(--transition-fast); }
.breadcrumb-item:hover { color: var(--primary); }
.exit-search { margin-left: 10px; color: var(--text-secondary); cursor: pointer; font-size: 13px; transition: color var(--transition-fast); }
.exit-search:hover { color: var(--primary); }
</style>
