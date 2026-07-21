<template>
  <div class="approval-page">
    <div class="approval-container">
      <h3 style="margin:0 0 10px 0">审批管理</h3>

      <div class="action-bar">
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width:120px" @change="search">
          <el-option label="待审批" :value="0" />
          <el-option label="已通过" :value="1" />
          <el-option label="已驳回" :value="2" />
          <el-option label="已撤回" :value="3" />
        </el-select>

        <template v-if="isDeptHead">
          <el-select v-model="filters.applicantId" placeholder="按申请人筛选" clearable filterable remote
            :remote-method="searchMembers" :loading="memberLoading" style="width:160px;margin-left:10px" @change="search">
            <el-option v-for="m in memberList" :key="m.userId" :label="m.nickName" :value="m.userId" />
          </el-select>
          <el-select v-model="filters.approverId" placeholder="按审批人筛选" clearable filterable remote
            :remote-method="searchMembers" :loading="memberLoading" style="width:160px;margin-left:10px" @change="search">
            <el-option v-for="m in memberList" :key="m.userId" :label="m.nickName" :value="m.userId" />
          </el-select>
        </template>

        <el-date-picker v-model="createTimeRange" type="daterange" range-separator="至"
          start-placeholder="申请时间从" end-placeholder="至" style="margin-left:10px"
          @change="onCreateTimeChange" />
        <el-date-picker v-model="handleTimeRange" type="daterange" range-separator="至"
          start-placeholder="处理时间从" end-placeholder="至" style="margin-left:10px"
          @change="onHandleTimeChange" />

        <el-button @click="resetFilters" style="margin-left:10px">重置</el-button>
      </div>

      <div class="table-area">
        <el-table :data="tableData" border stripe v-loading="loading" style="margin-top:10px">
          <template v-if="isDeptHead">
            <el-table-column prop="applicantName" label="申请人" width="120" />
            <el-table-column prop="approverName" label="审批人" width="120" />
          </template>
          <template v-else>
            <el-table-column prop="approverName" label="审批人" width="120" />
          </template>
          <el-table-column label="目标文件" width="200">
            <template #default="{ row }">
              <span v-if="!row.fileId">-</span>
              <span v-else-if="row.fileArchived" class="file-link-archived" @click="onFileNameClick(row)">{{ row.fileName }}</span>
              <span v-else-if="row.fileDeleted" class="file-link-deleted" @click="onFileNameClick(row)">{{ row.fileName }}</span>
              <span v-else class="file-link" @click="onFileNameClick(row)">{{ row.fileName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="申请时间" width="180">
            <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column prop="handleTime" label="处理时间" width="180">
            <template #default="{ row }">{{ row.handleTime ? fmtTime(row.handleTime) : '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button size="small" @click="showDetail(row)">详情</el-button>
              <el-button v-if="!isDeptHead && row.status === 0" size="small" type="warning" @click="withdraw(row)">撤回</el-button>
              <el-button v-if="!isDeptHead && row.status === 2" size="small" type="primary" @click="showResubmit(row)">重新提交</el-button>
              <el-button v-if="isDeptHead && row.status === 0" size="small" type="primary" @click="showHandleDialog(row)">处理</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <el-pagination
        v-model:current-page="pageNo"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next, total"
        @current-change="loadList"
        style="margin-top:15px;justify-content:center"
      />
    </div>

    <!-- 详情弹窗 -->
    <el-dialog v-model="showDetailDialog" title="申请详情" width="500px">
      <div class="detail-section">
        <div class="detail-label">申请内容：</div>
        <div class="detail-content">{{ detailRow.content }}</div>
      </div>
      <div class="detail-section" v-if="detailRow.comment">
        <div class="detail-label">审批意见：</div>
        <div class="detail-content">{{ detailRow.comment }}</div>
      </div>
      <template #footer>
        <el-button @click="showDetailDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 处理弹窗 -->
    <el-dialog v-model="showHandleDialogVisible" title="处理审批" width="500px" @closed="handleComment = ''">
      <el-input v-model="handleComment" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="审批意见（必填）" />
      <template #footer>
        <el-button @click="showHandleDialogVisible = false">取消</el-button>
        <el-button type="success" @click="doApprove" :disabled="!handleComment.trim()">同意</el-button>
        <el-button type="danger" @click="doReject" :disabled="!handleComment.trim()">驳回</el-button>
      </template>
    </el-dialog>
    <!-- 重新提交弹窗（已驳回） -->
    <el-dialog v-model="showResubmitDialog" title="重新提交申请" width="500px" @closed="resubmitContent = ''">
      <el-input v-model="resubmitContent" type="textarea" :rows="5" maxlength="500" show-word-limit placeholder="请输入申请内容" />
      <template #footer>
        <el-button @click="showResubmitDialog = false">取消</el-button>
        <el-button type="primary" @click="doResubmit" :disabled="!resubmitContent.trim()">提交</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, reactive, getCurrentInstance, onMounted, computed } from "vue";
import { useRouter } from "vue-router";
const { proxy } = getCurrentInstance();
const router = useRouter();
const userInfo = computed(() => proxy.VueCookies.get("userInfo"));
const isDeptHead = computed(() => userInfo.value?.deptHead);

const tableData = ref([]);
const loading = ref(false);
const pageNo = ref(1);
const pageSize = ref(15);
const total = ref(0);

const filters = reactive({ status: null, applicantId: "", approverId: "" });
const createTimeRange = ref(null);
const handleTimeRange = ref(null);
let createTimeStart = "";
let createTimeEnd = "";
let handleTimeStart = "";
let handleTimeEnd = "";

function fmtDate(d) {
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
}
function onCreateTimeChange(val) {
  if (val) {
    createTimeStart = fmtDate(val[0]) + "T00:00:00";
    createTimeEnd = fmtDate(val[1]) + "T23:59:59";
  } else { createTimeStart = ""; createTimeEnd = ""; }
  search();
}
function onHandleTimeChange(val) {
  if (val) {
    handleTimeStart = fmtDate(val[0]) + "T00:00:00";
    handleTimeEnd = fmtDate(val[1]) + "T23:59:59";
  } else { handleTimeStart = ""; handleTimeEnd = ""; }
  search();
}

function search() { pageNo.value = 1; loadList(); }

async function loadList() {
  loading.value = true;
  try {
    const params = { pageNo: pageNo.value, pageSize: pageSize.value };
    if (filters.status != null) params.status = filters.status;
    if (filters.applicantId) params.applicantId = filters.applicantId;
    if (filters.approverId) params.approverId = filters.approverId;
    if (createTimeStart) params.createTimeStart = createTimeStart;
    if (createTimeEnd) params.createTimeEnd = createTimeEnd;
    if (handleTimeStart) params.handleTimeStart = handleTimeStart;
    if (handleTimeEnd) params.handleTimeEnd = handleTimeEnd;
    const res = await proxy.Request({ url: "/approval/list", params });
    if (res?.data) { tableData.value = res.data.list; total.value = res.data.totalCount; }
  } finally { loading.value = false; }
}

function resetFilters() {
  filters.status = null; filters.applicantId = ""; filters.approverId = "";
  createTimeRange.value = null; handleTimeRange.value = null;
  createTimeStart = ""; createTimeEnd = ""; handleTimeStart = ""; handleTimeEnd = "";
  search();
}

const memberList = ref([]);
const memberLoading = ref(false);
async function searchMembers(query) {
  if (!query) { memberList.value = []; return; }
  memberLoading.value = true;
  try {
    const res = await proxy.Request({ url: "/auth/deptMembers", params: { keyword: query } });
    if (res?.data) memberList.value = res.data;
  } finally { memberLoading.value = false; }
}

async function withdraw(row) {
  proxy.Confirm("确定要撤回该申请吗？", async () => {
    await proxy.Request({ url: `/approval/withdraw/${row.id}`, method: "POST" });
    proxy.Message.success("已撤回");
    loadList();
  });
}

const showDetailDialog = ref(false);
const detailRow = ref({});
function showDetail(row) { detailRow.value = row; showDetailDialog.value = true; }

const showHandleDialogVisible = ref(false);
const handleRow = ref({});
const handleComment = ref("");
function showHandleDialog(row) { handleRow.value = row; handleComment.value = ""; showHandleDialogVisible.value = true; }
async function doApprove() {
  if (!handleComment.value.trim()) return;
  await proxy.Request({
    url: `/approval/approve/${handleRow.value.id}`, method: "POST",
    data: { comment: handleComment.value.trim() }, dataType: "json",
  });
  proxy.Message.success("已通过");
  showHandleDialogVisible.value = false;
  loadList();
}
async function doReject() {
  if (!handleComment.value.trim()) return;
  await proxy.Request({
    url: `/approval/reject/${handleRow.value.id}`, method: "POST",
    data: { comment: handleComment.value.trim() }, dataType: "json",
  });
  proxy.Message.success("已驳回");
  showHandleDialogVisible.value = false;
  loadList();
}

function statusType(s) { return { 0: "warning", 1: "success", 2: "danger", 3: "info" }[s] || ""; }
function statusLabel(s) { return { 0: "待审批", 1: "已通过", 2: "已驳回", 3: "已撤回" }[s] || s; }
function fmtTime(t) { return t ? t.replace("T", " ") : ""; }

// 点击目标文件名：已删除提示，未删除跳转到部门空间定位文件
function onFileNameClick(row) {
  if (!row.fileId) return;
  if (row.fileArchived) {
    proxy.Message.warning("文件已被归档");
    return;
  }
  if (row.fileDeleted) {
    proxy.Message.warning("文件已被删除");
    return;
  }
  if (!userInfo.value?.departmentId) {
    proxy.Message.warning("无所属部门，无法跳转");
    return;
  }
  const q = { deptId: row.fileDeptId };
  if (row.filePid && row.filePid !== "0") q.path = row.filePid;
  router.push({ path: "/main/all", query: q });
}

// 重新提交
const showResubmitDialog = ref(false);
const resubmitRow = ref({});
const resubmitContent = ref("");
function showResubmit(row) {
  resubmitRow.value = row;
  resubmitContent.value = row.content || "";
  showResubmitDialog.value = true;
}
async function doResubmit() {
  if (!resubmitContent.value.trim()) return;
  await proxy.Request({
    url: `/approval/resubmit/${resubmitRow.value.id}`, method: "POST",
    data: { content: resubmitContent.value.trim() }, dataType: "json",
  });
  proxy.Message.success("已重新提交");
  showResubmitDialog.value = false;
  loadList();
}

onMounted(loadList);
</script>

<style scoped>
.approval-page { display: flex; justify-content: center; padding: 20px; }
.approval-container { width: 100%; max-width: 1100px; }
.action-bar { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; }
.table-area { display: flex; justify-content: center; width: 100%; }
.table-area :deep(.el-table) { width: auto !important; }
.detail-section { margin-bottom: 15px; }
.detail-label { font-weight: bold; margin-bottom: 5px; }
.detail-content { padding: 10px; background: var(--bg-hover); border-radius: var(--radius-sm); white-space: pre-wrap; }
.file-link { color: var(--primary); cursor: pointer; }
.file-link:hover { text-decoration: underline; }
.file-link-deleted { color: var(--text-tertiary); cursor: pointer; text-decoration: line-through; }
.file-link-archived { color: var(--text-secondary); cursor: pointer; font-style: italic; font-weight: bold; }
</style>
