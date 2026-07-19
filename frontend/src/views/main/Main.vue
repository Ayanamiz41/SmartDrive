<template>
  <div>
    <!-- 管理员：部门选择页 -->
    <template v-if="userInfo.admin && !selectedDept">
      <div class="dept-home">
        <h2 style="text-align:center;margin:30px 0">选择部门空间</h2>
        <div class="dept-grid">
          <div v-for="d in deptList" :key="d.id" class="dept-card" @click="enterDept(d)">
            <span class="iconfont icon-team" style="font-size:48px;color:#409eff"></span>
            <div class="dept-name">{{ d.name }}</div>
          </div>
        </div>
      </div>
    </template>

    <!-- 文件列表视图 -->
    <template v-else>
    <div class="top">
      <div class="top-op">
        <!-- 管理员显示当前部门名，非管理员显示切换按钮 -->
        <template v-if="userInfo.admin">
          <span class="dept-label">{{ selectedDept?.name }}</span>
          <el-button size="small" @click="selectedDept=null" style="margin-left:10px" plain>返回部门列表</el-button>
        </template>
        <template v-else>
        <el-button
          :type="showDeptFiles ? 'primary' : ''"
          @click="toggleDeptFiles"
          :plain="!showDeptFiles"
        >
          <span class="iconfont icon-team"></span>
          {{ showDeptFiles ? '部门空间' : '我的文件' }}
        </el-button>
        </template>
        <el-divider direction="vertical" />
        <div class="btn">
          <el-button type="primary" @click="openUploadDialog">
            <span class="iconfont icon-upload"></span>
            上传
          </el-button>
        </div>
        <el-button type="success" @click="newFolder" v-if="category == 'all'" plain>
          <span class="iconfont icon-folder-add"></span>
          新建文件夹
        </el-button>
        <el-dropdown
          v-if="canWrite && selectFileIdList.length > 0"
          @command="handleBatchCommand"
        >
          <el-button type="primary">
            <span class="iconfont icon-batch"></span>
            批量操作 ({{ selectFileIdList.length }})
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="delete">
                <span class="iconfont icon-del"></span> 批量删除
              </el-dropdown-item>
              <el-dropdown-item command="move">
                <span class="iconfont icon-move"></span> 批量移动
              </el-dropdown-item>
              <el-dropdown-item command="archive">
                <span class="iconfont icon-import"></span> 批量归档
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <div class="search-panel">
          <el-input
            clearable
            placeholder="输入文件名搜索"
            v-model="fileNameFuzzy"
            @keyup.enter="search"
          >
            <template #suffix>
              <i class="iconfont icon-search" @click="search"></i>
            </template>
          </el-input>
        </div>
        <div style="position:relative; display:inline-block">
          <el-button :type="advFilterActive ? 'primary' : ''" @click="showAdvFilter = !showAdvFilter">高级筛选</el-button>
          <div v-show="showAdvFilter" class="adv-filter-panel">
          <el-form label-width="70px" size="small" @submit.prevent>
            <el-form-item label="上传者" v-if="showDeptFiles || (userInfo.admin && !!selectedDept)">
              <el-select
                v-model="advFilters.uploaderUserId"
                clearable
                filterable
                remote
                :remote-method="searchUploaders"
                :loading="uploaderLoading"
                placeholder="输入姓名搜索"
                style="width: 100%"
              >
                <el-option
                  v-for="u in uploaderList"
                  :key="u.userId"
                  :label="u.nickName"
                  :value="u.userId"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="创建时间">
              <el-date-picker
                v-model="advFilters.createTimeRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                style="width: 100%"
              />
            </el-form-item>
            <div style="text-align: right">
              <el-button size="small" @click="resetAdvFilters">重置</el-button>
              <el-button size="small" type="primary" @click="search">搜索</el-button>
            </div>
          </el-form>
          </div>
        </div>
        <div class="iconfont icon-refresh" @click="loadDataList"></div>
      </div>
      <!--导航：搜索态下切换为搜索结果提示（v-show 保留 Navigation 实例，openFolder/watch 依赖它）-->
      <Navigation v-show="!isSearchResult" ref="navigationRef" @navChange="navChange" :adminShow="isDeptSpace"></Navigation>
      <div v-show="isSearchResult" class="search-result-bar">
        <span class="label">搜索结果</span>
        <span class="exit-search" @click="exitSearch">退出搜索</span>
      </div>
    </div>
    <div class="file-list" v-if="tableData.list && tableData.list.length > 0">
      <Table
        ref="dataTableRef"
        :columns="columns"
        :showPagination="true"
        :dataSource="tableData"
        :fetch="loadDataList"
        :initFetch="false"
        :options="tableOptions"
        @rowSelected="rowSelected"
        @sortChange="handleSortChange"
      >
        <template #fileName="{ index, row }">
          <div>
            <div
              class="file-item"
              @mouseenter="showOp(row)"
              @mouseleave="cancelShowOp(row)"
            >
              <span class="summary-tag" @click.stop="toggleSummary(row)">摘要</span>
              <template
                v-if="(row.fileType == 3 || row.fileType == 1) && row.status == 2"
              >
                <icon :cover="row.fileCover" :width="32"></icon>
              </template>
              <template v-else>
                <icon v-if="row.folderType == 0" :fileType="row.fileType"></icon>
                <icon v-if="row.folderType == 1" :fileType="0"></icon>
              </template>
              <span class="file-name" v-if="!row.showEdit" :title="row.fileName">
                <span @click="preview(row)">{{ row.fileName }}</span>
                <span v-if="row.status == 0" class="transfer-status">转码中</span>
                <span v-if="row.status == 1" class="transfer-status transfer-fail"
                  >转码失败</span
                >
                <div
                  v-if="row.pathList"
                  class="file-path"
                  :title="'全部文件' + row.pathList.map((p) => ' / ' + p.fileName).join('')"
                  @click.stop="jumpToFolder(row)"
                >
                  全部文件<template v-for="p in row.pathList"> / {{ p.fileName }}</template>
                </div>
              </span>
              <div class="edit-panel" v-if="row.showEdit">
                <el-input
                  v-model.trim="row.fileNameReal"
                  ref="editNameRef"
                  :maxLength="190"
                  @keyup.enter="saveNameEdit(index)"
                >
                  <template #suffix>{{ row.fileSuffix }}</template>
                </el-input>
                <span
                  :class="[
                    'iconfont icon-right1',
                    row.fileNameReal ? '' : 'not-allow',
                  ]"
                  @click="saveNameEdit(index)"
                ></span>
                <span
                  class="iconfont icon-error"
                  @click="cancelNameEdit(index)"
                ></span>
              </div>
              <span class="op" v-if="!row.showEdit && row.showOp && row.fileId && row.status == 2">
                <span v-if="!userInfo.admin && userInfo.deptHead && canWrite" class="iconfont icon-share1" @click="share(row)"
                  >分享</span
                >
                <span
                  class="iconfont icon-download"
                  @click="download(row)"
                  v-if="row.folderType == 0"
                  >下载</span
                >
                <span class="iconfont icon-upload" @click="copyToDept(row)" v-if="!row.departmentId && userInfo.departmentId"
                  style="color:#67c23a;margin-left:4px" title="上传到部门空间">上传部门</span>
                <span class="iconfont icon-download" @click="copyToPersonal(row)" v-if="row.departmentId && !userInfo.admin"
                  style="color:#e6a23c;margin-left:4px" title="转存到个人空间">转存个人</span>
                <span class="iconfont icon-doc" @click="applyApproval(row)" v-if="row.departmentId && !userInfo.admin && !userInfo.deptHead"
                  style="color:#409eff;margin-left:4px" title="提出申请">提出申请</span>
                <span
                  class="iconfont icon-import"
                  @click="archiveFile(row)"
                  v-if="row.departmentId && !row.archived && (userInfo.admin || userInfo.deptHead)"
                  style="color:#e6a23c"
                  >归档</span
                >
                <span class="iconfont icon-del" @click="delFile(row)" v-if="canWrite"
                  >删除</span
                >
                <span
                  class="iconfont icon-edit"
                  @click.stop="editFileName(index)"
                  v-if="canWrite"
                  >重命名</span
                >
                <span class="iconfont icon-move" @click="moveFolder(row)" v-if="canWrite"
                  >移动</span
                >
              </span>
            </div>
            <div class="summary-inline" v-if="row.summaryExpanded && !row.showEdit" @click.stop>
              <template v-if="!row.summaryEditing">
                <span class="summary-inline-text">{{ row.summary }}</span>
                <span v-if="canEditSummary(row)" class="summary-inline-edit" @click="startEditSummary(row)">编辑</span>
              </template>
              <template v-else>
                <div class="summary-edit-row">
                  <el-input v-model="row.summaryDraft" type="textarea" :rows="2" maxlength="200" show-word-limit size="small" :placeholder="row.summaryPlaceholder || '输入文件摘要...'" />
                  <div class="summary-edit-actions">
                    <el-button size="small" @click="aiGenerateSummary(row)" :loading="row.aiLoading">AI摘要</el-button>
                    <span class="iconfont icon-right1" @click="saveSummary(row)" title="保存"></span>
                    <span class="iconfont icon-error" @click="cancelEditSummary(row)" title="取消"></span>
                  </div>
                </div>
              </template>
            </div>
          </div>
        </template>
        <template #fileSize="{ index, row }">
          <span v-if="row.fileSize">
            {{ proxy.Utils.size2Str(row.fileSize) }}</span
          >
        </template>
      </Table>
    </div>
    <div class="no-data" v-else>
      <div class="no-data-inner">
        <Icon iconName="no_data" :width="120" fit="fill"></Icon>
        <div class="tips">当前目录为空，上传你的第一个文件吧</div>
        <div class="op-list">
          <div class="op-item" @click="openUploadDialog">
            <Icon iconName="file" :width="60"></Icon>
            <div>上传文件</div>
          </div>
          <div class="op-item" v-if="category == 'all'" @click="newFolder">
            <Icon iconName="folder" :width="60"></Icon>
            <div>新建目录</div>
          </div>
        </div>
      </div>
    </div>
    <!--预览-->
    <Preview ref="previewRef"> </Preview>
    <!--移动-->
    <FolderSelect
      ref="folderSelectRef"
      :deptMode="showDeptFiles || (userInfo.admin && !!selectedDept)"
      :departmentId="selectedDept?.id"
      @folderSelect="moveFolderDone"
    ></FolderSelect>
    <!--分享-->
    <FileShare ref="shareRef"></FileShare>
    <!--上传部门-->
    <FolderSelect ref="copyToDeptFolderRef" :deptMode="true" :departmentId="userInfo.departmentId" @folderSelect="copyToDeptDone" />
    <!--转存个人-->
    <FolderSelect ref="copyToPersonalFolderRef" :deptMode="false" @folderSelect="copyToPersonalDone" />
    <!--上传弹框-->
    <el-dialog v-model="uploadDialogVisible" title="上传文件" width="480px" :close-on-click-modal="false" @closed="resetUploadDialog">
      <el-form label-width="80px" label-position="right">
        <el-form-item label="选择文件">
          <el-upload ref="uploadRef" :auto-upload="false" :show-file-list="false" :accept="fileAccept" :on-change="onUploadFileSelect">
            <el-button type="primary">选择文件</el-button>
          </el-upload>
          <div v-if="selectedUploadFile" class="upload-file-name">{{ selectedUploadFile.name }}</div>
        </el-form-item>
        <el-form-item label="摘要" class="form-item-top">
          <el-input v-model="uploadSummary" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="输入文件摘要（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmUpload" :disabled="!selectedUploadFile">开始上传</el-button>
      </template>
    </el-dialog>
    <!-- 提出申请弹窗 -->
    <el-dialog v-model="showApprovalDialog" title="提出申请" width="500px" @closed="approvalContent = ''">
      <el-input v-model="approvalContent" type="textarea" :rows="5" maxlength="500" show-word-limit placeholder="请输入申请内容" />
      <template #footer>
        <el-button @click="showApprovalDialog = false">取消</el-button>
        <el-button type="primary" @click="doApplyApproval" :disabled="!approvalContent.trim()">提交</el-button>
      </template>
    </el-dialog>
    </template>
  </div>
</template>

<script setup>
import CategoryInfo from "@/js/CategoryInfo.js";
import FileShare from "./ShareFile.vue";
import { ElMessageBox } from "element-plus";
import { ref, reactive, getCurrentInstance, nextTick, computed, watch } from "vue";
import { useRouter, useRoute } from "vue-router";
const { proxy } = getCurrentInstance();
const router = useRouter();
const route = useRoute();
const userInfo = proxy.VueCookies.get("userInfo");
const showDeptFiles = ref(!!(route.query.deptId && !userInfo.admin));
const selectedDept = ref(null);
const deptList = ref([]);

const isDeptSpace = computed(() => showDeptFiles.value || (userInfo.admin && !!selectedDept.value));
const canWrite = computed(() => {
  if (!isDeptSpace.value) return true;
  if (userInfo.admin) return true;
  return userInfo.deptHead;
});

async function loadDeptList() {
  const res = await proxy.Request({ url: "/admin/department/list" });
  if (res && res.data) deptList.value = res.data;
}

// 切换空间/部门时重置导航到根目录：清 URL path + 面包屑，避免带着旧空间的 filePid 查询
function resetNavigation() {
  if (route.query.path) {
    // 清掉 ?path=，Navigation 的 route watch 会自动 init → navChange → loadDataList
    router.replace({ path: route.path, query: {} });
  } else {
    nextTick(() => {
      if (navigationRef.value) {
        navigationRef.value.init();
      } else {
        loadDataList();
      }
    });
  }
}

function enterDept(dept) {
  selectedDept.value = dept;
  showLoading.value = true;
  resetNavigation();
}

function toggleDeptFiles() {
  if (!showDeptFiles.value && !userInfo.departmentId) {
    proxy.Message.warning("您不属于任何部门，无法访问部门空间");
    return;
  }
  showDeptFiles.value = !showDeptFiles.value;
  showLoading.value = true;
  resetNavigation();
}

// 从审批/审计页跳转过来：开启部门空间并定位到文件目录
async function handleDeptJump(deptId, filePid) {
  if (!deptId) return;
  if (userInfo.admin) {
    if (deptList.value.length === 0) await loadDeptList();
    if (!selectedDept.value || selectedDept.value.id !== deptId) {
      const dept = deptList.value.find(d => d.id === deptId);
      if (dept) selectedDept.value = dept;
    }
  } else {
    if (!showDeptFiles.value) showDeptFiles.value = true;
  }
  showLoading.value = true;
}
const emit = defineEmits(["addFile"]);

// 上传弹框
const uploadDialogVisible = ref(false);
const selectedUploadFile = ref(null);
const uploadSummary = ref("");

const openUploadDialog = () => {
  uploadDialogVisible.value = true;
};

const onUploadFileSelect = (file) => {
  selectedUploadFile.value = file.raw;
};

const confirmUpload = async () => {
  if (!selectedUploadFile.value) return;
  const fileName = selectedUploadFile.value.name;
  const fid = currentFolder.value.fileId;
  const isDept = showDeptFiles.value || (userInfo.admin && !!selectedDept.value);
  const deptId = selectedDept.value?.id;
  const dup = await checkDuplicate(fid, fileName, isDept, deptId);
  proxy.Confirm(dup ? `该目录下已经有同名文件【${fileName}】，是否继续？` : `确定上传【${fileName}】吗？`, () => {
    emit("addFile", {
      file: selectedUploadFile.value,
      filePid: fid,
      deptMode: isDept,
      departmentId: deptId,
      summary: uploadSummary.value,
    });
    uploadDialogVisible.value = false;
  });
};

const resetUploadDialog = () => {
  selectedUploadFile.value = null;
  uploadSummary.value = "";
};

//添加文件回调
const reload = () => {
  showLoading.value = false;
  loadDataList();
};
defineExpose({
  reload,
});

const currentFolder = ref({ fileId: 0 });

const api = {
  loadDataList: "/file/loadDataList",
  rename: "/file/rename",
  newFolder: "/file/newFolder",
  getFolderInfo: "/file/getFolderInfo",
  delFile: "/file/delFile",
  changeFileFolder: "/file/changeFileFolder",
  createDownloadUrl: "/file/createDownloadUrl",
  download: "/api/file/download",
  editSummary: "/file/editSummary",
};

const fileAccept = computed(() => {
  const categoryItem = CategoryInfo[category.value];
  return categoryItem ? categoryItem.accept : "*";
});

//列表
const columns = [
  {
    label: "文件名",
    prop: "fileName",
    scopedSlots: "fileName",
    minWidth: 300,
  },
  {
    label: "上传者",
    prop: "nickName",
    width: 120,
  },
  {
    label: "最后编辑",
    prop: "lastUpdateUserNickName",
    width: 120,
  },
  {
    label: "修改时间",
    prop: "lastUpdateTime",
    width: 180,
    sortable: true,
  },
  {
    label: "创建时间",
    prop: "createTime",
    width: 180,
    sortable: true,
  },
  {
    label: "大小",
    prop: "fileSize",
    scopedSlots: "fileSize",
    width: 120,
    sortable: true,
  },
];
//搜索
const search = () => {
  showAdvFilter.value = false;
  showLoading.value = true;
  loadDataList();
};
const handleSortChange = ({ prop, order }) => {
  sortField.value = order ? prop : null;
  sortOrder.value = order;
  tableData.value.pageNo = 1;
  loadDataList();
};
//列表
const tableData = ref({});
const tableOptions = {
  extHeight: 50,
  selectType: "checkbox",
};

const fileNameFuzzy = ref();
const showLoading = ref(true);
const category = ref();
const sortField = ref(null);
const sortOrder = ref(null);

// 高级筛选条件
const advFilters = ref({
  uploaderUserId: "",
  createTimeRange: null,
});
const showAdvFilter = ref(false);
const advFilterActive = computed(() => {
  const f = advFilters.value;
  return !!(f.uploaderUserId || (f.createTimeRange && f.createTimeRange.length === 2));
});
const resetAdvFilters = () => {
  showAdvFilter.value = false;
  advFilters.value = { uploaderUserId: "", createTimeRange: null };
  showLoading.value = true;
  loadDataList();
};
// 上传者远程搜索（仿审计操作者筛选：输入前无下拉，输入后匹配才出现）
const uploaderList = ref([]);
const uploaderLoading = ref(false);

const searchUploaders = async (query) => {
  if (!query) { uploaderList.value = []; return; }
  uploaderLoading.value = true;
  let res;
  if (userInfo.admin) {
    res = await proxy.Request({
      url: "/admin/loadUserList",
      params: { nickNameFuzzy: query, pageSize: 20 },
      showLoading: false,
    });
  } else {
    const params = { nickNameFuzzy: query };
    if (selectedDept.value) params.departmentId = selectedDept.value.id;
    res = await proxy.Request({
      url: "/auth/deptMembers",
      params,
      showLoading: false,
    });
  }
  uploaderList.value = res?.data?.list || res?.data || [];
  uploaderLoading.value = false;
};

// 当前列表是否为搜索结果（控制面包屑区显示；请求成功后才切换，避免输入未提交时抖动）
const isSearchResult = ref(false);
const exitSearch = () => {
  fileNameFuzzy.value = "";
  advFilters.value = { uploaderUserId: "", createTimeRange: null };
  showLoading.value = true;
  loadDataList(); // currentFolder 未动 → 回到搜索前所在目录
};

const loadDataList = async () => {
  let params = {
    pageNo: tableData.value.pageNo || 1,
    pageSize: tableData.value.pageSize || 15,
    fileNameFuzzy: fileNameFuzzy.value,
    category: category.value,
    filePid: currentFolder.value.fileId,
  };
  if (showDeptFiles.value) {
    params.deptMode = true;
  }
  if (userInfo.admin && selectedDept.value) {
    params.departmentId = selectedDept.value.id;
  }
  if (params.category !== "all") {
    delete params.filePid;
  }
  // 关键字或任一高级筛选生效 → 走 ES 搜索（全局，不限当前目录）
  if (params.fileNameFuzzy || advFilterActive.value) {
    delete params.filePid;
    params.keyword = params.fileNameFuzzy || "";
    delete params.fileNameFuzzy;
    const f = advFilters.value;
    if (f.uploaderUserId) {
      params.uploaderUserId = f.uploaderUserId;
    }
    if (f.createTimeRange && f.createTimeRange.length === 2) {
      params.createTimeStart = f.createTimeRange[0].getTime();
      // 结束日取当天 23:59:59.999
      params.createTimeEnd = f.createTimeRange[1].getTime() + 86399999;
    }
  }
  // 排序
  if (sortField.value && sortOrder.value) {
    if (params.keyword !== undefined) {
      params.sortField = sortField.value;
      params.sortOrder = sortOrder.value;
    } else {
      const SORT_MAP = { lastUpdateTime: 'last_update_time', createTime: 'create_time', fileSize: 'file_size' };
      const dbField = SORT_MAP[sortField.value];
      if (dbField) params.orderBy = dbField + ' ' + (sortOrder.value === 'ascending' ? 'asc' : 'desc');
    }
  }
  let url = params.keyword !== undefined ? "/file/search" : api.loadDataList;
  let result = await proxy.Request({
    url: url,
    showLoading: showLoading,
    params,
  });
  if (!result) {
    return;
  }
  tableData.value = result.data;
  isSearchResult.value = params.keyword !== undefined;
  editing.value = false;
};

if (userInfo.admin) {
  loadDeptList();
}
// 非 admin 的首屏加载由 Navigation 的 immediate route watch 触发（navChange → loadDataList）。
// 此处不能再直调 loadDataList：直调时 category 尚未初始化（undefined ≠ "all"）会误删 filePid，
// 触发全局查询，与 navChange 的正确请求形成竞态——先显示全部层级文件再收缩为根目录。

//展示操作按钮
const showOp = (row) => {
  tableData.value.list.forEach((element) => {
    element.showOp = false;
  });
  row.showOp = true;
};

const cancelShowOp = (row) => {
  row.showOp = false;
};

//编辑行
const editing = ref(false);
const editNameRef = ref();
//新建文件夹
const newFolder = () => {
  if (editing.value) {
    return;
  }
  tableData.value.list.forEach((element) => {
    element.showEdit = false;
  });
  editing.value = true;
  tableData.value.list.unshift({
    showEdit: true,
    fileType: 0,
    fileId: "",
    filePid: currentFolder.value.fileId,
  });
  nextTick(() => {
    editNameRef.value.focus();
  });
};

const cancelNameEdit = (index) => {
  const fileData = tableData.value.list[index];
  if (fileData.fileId) {
    fileData.showEdit = false;
  } else {
    tableData.value.list.splice(index, 1);
  }
  editing.value = false;
};

const saveNameEdit = async (index) => {
  const { fileId, filePid, fileNameReal } = tableData.value.list[index];
  if (fileNameReal == "" || fileNameReal.indexOf("/") != -1) {
    proxy.Message.warning("文件名不能为空且不能含有斜杠");
    return;
  }
  let url = api.rename;
  if (fileId == "") {
    url = api.newFolder;
  }
  const isDept = showDeptFiles.value || (userInfo.admin && !!selectedDept.value);
  const deptId = selectedDept.value?.id;
  let result = await proxy.Request({
    url,
    params: { fileId, filePid, fileName: fileNameReal, deptMode: isDept, departmentId: deptId },
    showError: false,
  });
  if (!result) {
    ElMessageBox.confirm("该目录已有同名文件，是否自动重命名并继续？", "重命名", {
      confirmButtonText: "继续",
      cancelButtonText: "取消",
      type: "info",
    }).then(async () => {
      let retry = await proxy.Request({
        url,
        params: { fileId, filePid, fileName: fileNameReal, deptMode: isDept, departmentId: deptId, autoRename: true },
      });
      if (!retry) return;
      tableData.value.list[index] = retry.data;
      editing.value = false;
    }).catch(() => {});
    return;
  }
  tableData.value.list[index] = result.data;
  editing.value = false;
};

//编辑文件名
const editFileName = (index) => {
  if (tableData.value.list[0].fileId == "") {
    tableData.value.list.splice(0, 1);
    index = index - 1;
  }
  tableData.value.list.forEach((element) => {
    element.showEdit = false;
  });
  let cureentData = tableData.value.list[index];
  cureentData.showEdit = true;

  //编辑文件
  if (cureentData.folderType == 0) {
    cureentData.fileNameReal = cureentData.fileName.substring(
      0,
      cureentData.fileName.indexOf(".")
    );
    cureentData.fileSuffix = cureentData.fileName.substring(
      cureentData.fileName.indexOf(".")
    );
  } else {
    cureentData.fileNameReal = cureentData.fileName;
    cureentData.fileSuffix = "";
  }
  editing.value = true;
  nextTick(() => {
    editNameRef.value.focus();
  });
};

//多选 批量选择
const selectFileIdList = ref([]);
const selectFileList = ref([]);
const rowSelected = (rows) => {
  selectFileList.value = rows;
  selectFileIdList.value = [];
  rows.forEach((item) => {
    selectFileIdList.value.push(item.fileId);
  });
};

async function checkDuplicate(folderId, fileName, deptMode, deptId) {
  if (!folderId || !fileName) return false;
  try {
    const params = { fileName, filePid: folderId, deptMode };
    if (deptId) params.departmentId = deptId;
    const res = await proxy.Request({ url: "/file/checkDuplicate", params, showLoading: false });
    return res?.data?.duplicate === true;
  } catch (e) { return false; }
}

//删除文件
const delFile = (row) => {
  const msg = userInfo.admin
    ? `确定要永久删除【${row.fileName}】吗？此操作不可恢复`
    : `你确定要删除【${row.fileName}】吗？删除的文件可在10天内通过回收站还原`;
  proxy.Confirm(msg,
    async () => {
      let result = await proxy.Request({
        url: api.delFile,
        params: {
          fileIds: row.fileId,
        },
      });
      if (!result) {
        return;
      }
      loadDataList();
    }
  );
};
//批量删除
const delFileBatch = () => {
  if (selectFileIdList.value.length == 0) {
    return;
  }
  const msg = userInfo.admin
    ? "确定要永久删除这些文件吗？此操作不可恢复"
    : "你确定要删除这些文件吗？删除的文件可在10天内通过回收站还原";
  proxy.Confirm(msg,
    async () => {
      let result = await proxy.Request({
        url: api.delFile,
        params: {
          fileIds: selectFileIdList.value.join(","),
        },
      });
      if (!result) {
        return;
      }
      loadDataList();
    }
  );
};

//移动目录
const folderSelectRef = ref();
const currentMoveFile = ref({});
const moveFolder = (data) => {
  currentMoveFile.value = data;
  folderSelectRef.value.showFolderDialog(data.fileId);
};

//批量移动
const moveFolderBatch = () => {
  currentMoveFile.value = {};
  //批量移动如果选择的是文件夹，那么要讲文件夹也过滤
  const excludeFileIdList = [currentFolder.value.fileId];
  selectFileList.value.forEach((item) => {
    if (item.folderType == 1) {
      excludeFileIdList.push(item.fileId);
    }
  });
  folderSelectRef.value.showFolderDialog(excludeFileIdList.join(","));
};

const moveFolderDone = async (folderId) => {
  if (
    currentMoveFile.value.filePid === folderId ||
    currentFolder.value.fileId == folderId
  ) {
    proxy.Message.warning("文件正在当前目录，无需移动");
    return;
  }
  const isDept = showDeptFiles.value || (userInfo.admin && !!selectedDept.value);
  const deptId = selectedDept.value?.id;
  const fileName = currentMoveFile.value.fileId
    ? currentMoveFile.value.fileName
    : selectFileList.value[0]?.fileName || "";
  let msg = currentMoveFile.value.fileId
    ? `确定要移动【${fileName}】吗？`
    : `确定要移动选中的 ${selectFileIdList.value.length} 个文件吗？`;
  const dup = await checkDuplicate(folderId, fileName, isDept, deptId);
  if (dup) msg = "该目录下已经有同名文件，是否继续？";
  proxy.Confirm(msg, async () => {
    let filedIdsArray = [];
    if (currentMoveFile.value.fileId) {
      filedIdsArray.push(currentMoveFile.value.fileId);
    } else {
      filedIdsArray = filedIdsArray.concat(selectFileIdList.value);
    }
    let result = await proxy.Request({
      url: api.changeFileFolder,
      params: { fileIds: filedIdsArray.join(","), filePid: folderId },
    });
    if (!result) return;
    folderSelectRef.value.close();
    loadDataList();
  });
};

const previewRef = ref();
const navigationRef = ref();
const preview = (data) => {
  if (data.folderType == 1) {
    //openFolder(data);
    navigationRef.value.openFolder(data);
    return;
  }
  if (data.status != 2) {
    proxy.Message.warning("文件正在转码中，无法预览");
    return;
  }
  previewRef.value.showPreview(data, 0);
};

//目录
const navChange = (data) => {
  const { curFolder, categoryId } = data;
  // 从搜索结果进入具体文件夹 → 退出搜索态（清关键字+高级筛选），否则列表仍是搜索结果、"进不去"
  if (curFolder.fileId != "0" && (fileNameFuzzy.value || advFilterActive.value)) {
    fileNameFuzzy.value = "";
    advFilters.value = { uploaderUserId: "", createTimeRange: null };
  }
  currentFolder.value = curFolder;
  showLoading.value = true;
  category.value = categoryId;
  loadDataList();
};

// 搜索结果"所在位置"跳转：退出搜索态并导航到目标文件夹
const jumpToFolder = (row) => {
  fileNameFuzzy.value = "";
  advFilters.value = { uploaderUserId: "", createTimeRange: null };
  const pathStr = (row.pathList || []).map((p) => p.fileId).join("/");
  if (pathStr) {
    // 路由变化触发 Navigation 的 watch → 面包屑重建 → navChange → loadDataList
    router.push({ path: route.path, query: { path: pathStr } });
  } else {
    // 根目录：路由可能无变化不触发 watch，直接重载
    showLoading.value = true;
    loadDataList();
  }
};

//下载文件
const download = async (row) => {
  let result = await proxy.Request({
    url: api.createDownloadUrl + "/" + row.fileId,
  });
  if (!result) {
    return;
  }
  window.location.href = api.download + "/" + result.data;
};

// 上传部门：个人文件拷到部门空间
const copyToDeptFile = ref(null);
const copyToDeptFolderRef = ref();
const copyToDept = (row) => {
  copyToDeptFile.value = row;
  copyToDeptFolderRef.value.showFolderDialog(row.fileId);
};
const copyToDeptDone = async (folderId) => {
  const row = copyToDeptFile.value;
  if (!row || !userInfo.departmentId) return;
  const dup = await checkDuplicate(folderId, row.fileName, true, userInfo.departmentId);
  proxy.Confirm(dup ? "该目录下已经有同名文件，是否继续？"
    : `确定要将【${row.fileName}】上传到部门空间吗？`, async () => {
    let result = await proxy.Request({
      url: "/file/copyToDept",
      params: { fileId: row.fileId, targetFolderId: folderId, departmentId: userInfo.departmentId },
    });
    if (!result) return;
    copyToDeptFolderRef.value.close();
    proxy.Message.success("已上传到部门空间");
  });
};

// 转存个人：部门文件拷到个人空间
const copyToPersonalFile = ref(null);
const copyToPersonalFolderRef = ref();
const copyToPersonal = (row) => {
  copyToPersonalFile.value = row;
  copyToPersonalFolderRef.value.showFolderDialog(row.fileId);
};
const copyToPersonalDone = async (folderId) => {
  const row = copyToPersonalFile.value;
  if (!row) return;
  const dup = await checkDuplicate(folderId, row.fileName, false, null);
  proxy.Confirm(dup ? "该目录下已经有同名文件，是否继续？"
    : `确定要将【${row.fileName}】转存到个人空间吗？`, async () => {
    let result = await proxy.Request({
      url: "/file/copyToPersonal",
      params: { fileId: row.fileId, targetFolderId: folderId },
    });
    if (!result) return;
    copyToPersonalFolderRef.value.close();
    proxy.Message.success("已转存到个人空间");
  });
};

// 提出申请
const showApprovalDialog = ref(false);
const approvalContent = ref("");
const approvalFile = ref(null);
const applyApproval = (row) => {
  approvalFile.value = row;
  approvalContent.value = "";
  showApprovalDialog.value = true;
};
const doApplyApproval = async () => {
  if (!approvalContent.value.trim()) return;
  await proxy.Request({
    url: "/approval/submit", method: "POST",
    data: { content: approvalContent.value.trim(), fileId: approvalFile.value.fileId, fileName: approvalFile.value.fileName }, dataType: "json",
  });
  proxy.Message.success("申请已提交");
  showApprovalDialog.value = false;
  approvalContent.value = "";
};

//分享
const shareRef = ref();
const share = (row) => {
  shareRef.value.show(row);
};

//归档
const archiveFile = (row) => {
  proxy.Confirm(`确定要将【${row.fileName}】归档吗？归档后文件将变为只读。`, async () => {
    let result = await proxy.Request({
      url: "/file/archive",
      params: { fileIds: row.fileId },
    });
    if (!result) return;
    proxy.Message.success("已归档");
    loadDataList();
  });
};

// 批量操作下拉分发
const handleBatchCommand = (command) => {
  if (command === 'delete') delFileBatch();
  else if (command === 'move') moveFolderBatch();
  else if (command === 'archive') archiveFileBatch();
};

//批量归档
const archiveFileBatch = () => {
  proxy.Confirm(`确定要将选中的 ${selectFileIdList.value.length} 个文件归档吗？归档后文件将变为只读。`, async () => {
    let result = await proxy.Request({
      url: "/file/archive",
      params: { fileIds: selectFileIdList.value.join(",") },
    });
    if (!result) return;
    proxy.Message.success("批量归档完成");
    loadDataList();
  });
};

// === 摘要 ===
const toggleSummary = (row) => {
  row.summaryExpanded = !row.summaryExpanded;
  if (!row.summaryExpanded) row.summaryEditing = false;
};

const editSummary = (row) => {
  row.summaryExpanded = true;
  startEditSummary(row);
};

// === 摘要编辑 ===
const canEditSummary = (row) => {
  // 个人空间文件：只有上传者可以编辑
  if (!row.departmentId) {
    return row.userId === userInfo.userId;
  }
  // 部门空间文件：仅 admin / 部门主管可编辑
  if (userInfo.admin) return true;
  return userInfo.deptHead;
};

const startEditSummary = (row) => {
  row.summaryExpanded = true;
  row.summaryDraft = row.summary || "";
  row.summaryPlaceholder = "";
  row.summaryEditing = true;
  row.aiLoading = false;
};

const cancelEditSummary = (row) => {
  row.summaryEditing = false;
  row.summaryDraft = "";
  if (!row.summary) row.summaryExpanded = false;
};

const saveSummary = async (row) => {
  const summary = (row.summaryDraft || "").trim();
  if (summary.length > 200) {
    proxy.Message.warning("摘要不能超过200字");
    return;
  }
  let result = await proxy.Request({
    url: api.editSummary,
    params: {
      fileId: row.fileId,
      summary: summary,
    },
  });
  if (!result) return;
  row.summary = summary || null;
  row.summaryEditing = false;
  row.summaryExpanded = false;
  row.summaryDraft = "";
  proxy.Message.success("摘要已更新");
};

const aiGenerateSummary = async (row) => {
  row.aiLoading = true;
  try {
    let result = await proxy.Request({
      url: "/file/aiSummary",
      params: { fileId: row.fileId },
      showLoading: false,
    });
    if (result && result.data) {
      const text = result.data;
      if (/^(AI摘要|该文件类型|请先配置|AI接口)/.test(text)) {
        row.summaryPlaceholder = text;
        row.summaryDraft = "";
      } else {
        row.summaryPlaceholder = "";
        row.summaryDraft = text;
      }
    }
  } catch (e) {
    proxy.Message.error("AI摘要生成失败");
  } finally {
    row.aiLoading = false;
  }
};

// 监听来自审批页的跳转参数
watch(
  () => route.query,
  (q) => {
    if (q.deptId) {
      handleDeptJump(q.deptId, q.filePid);
    }
  },
  { immediate: true }
);
</script>

<style lang="scss" scoped>
@import "@/assets/file.list.scss";
.file-path {
  font-size: 12px;
  color: var(--text-tertiary);
  line-height: 16px;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color var(--transition-fast);
  &:hover {
    color: var(--text-secondary);
  }
}
.search-result-bar {
  font-size: 13px;
  line-height: 40px;
  .label {
    font-weight: 600;
    color: var(--text-primary);
  }
  .exit-search {
    margin-left: 10px;
    color: var(--text-secondary);
    cursor: pointer;
    font-size: 13px;
    transition: color var(--transition-fast), transform var(--transition-fast);
    &:hover { color: var(--primary); transform: scale(1.1); }
  }
}
.dept-home { max-width:1000px; margin:0 auto; }
.dept-grid { display:flex; flex-wrap:wrap; gap:20px; justify-content:center; }
.dept-card {
  width:200px; padding:30px 20px; border-radius:var(--radius-lg); text-align:center;
  cursor:pointer; background:#fff; border:1px solid var(--border-color);
  transition: box-shadow var(--transition-fast);
  animation: fadeInUp 0.35s ease both;
  .iconfont { color: var(--primary); }
}
.dept-card:nth-child(1) { animation-delay: 0.05s; }
.dept-card:nth-child(2) { animation-delay: 0.1s; }
.dept-card:nth-child(3) { animation-delay: 0.15s; }
.dept-card:nth-child(4) { animation-delay: 0.2s; }
.dept-card:nth-child(5) { animation-delay: 0.25s; }
.dept-card:nth-child(6) { animation-delay: 0.3s; }
.dept-card:hover {
  border-color: var(--primary);
  box-shadow: var(--shadow-md);
}
.dept-name { margin-top:12px; font-size:16px; font-weight:500; color: var(--text-primary); }
.dept-label { font-size:14px; color: var(--primary); font-weight:600; }
.summary-tag {
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  margin-right: 8px;
  padding: 0 6px;
  border: 1px solid var(--border-color);
  border-radius: 3px;
  white-space: nowrap;
  transition: all var(--transition-fast);
  &:hover { background: var(--bg-hover); color: var(--primary); border-color: var(--primary); }
}
.summary-inline {
  padding: 8px 12px 8px 40px;
  background: var(--bg-hover);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: flex-start;
  gap: 12px;
  .summary-inline-text {
    flex: 1;
    font-size: 13px;
    color: var(--text-secondary);
    line-height: 1.6;
    word-break: break-all;
  }
  .summary-inline-edit {
    flex-shrink: 0;
    font-size: 12px;
    color: var(--text-secondary);
    cursor: pointer;
    transition: color var(--transition-fast);
    &:hover { color: var(--primary); text-decoration: underline; }
  }
  .summary-edit-row {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  .summary-edit-actions {
    display: flex;
    align-items: center;
    gap: 6px;
    .iconfont {
      cursor: pointer;
      color: var(--text-secondary);
      font-size: 16px;
      transition: color var(--transition-fast);
      &:hover { color: var(--primary); }
    }
    .icon-right1 {
      background: var(--primary);
      color: #fff;
      padding: 2px 6px;
      border-radius: var(--radius-sm);
      font-size: 14px;
      transition: background var(--transition-fast);
      &:hover { background: var(--primary-hover); }
    }
    .icon-error { color: var(--text-tertiary); }
  }
}
.upload-file-name {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-secondary);
  word-break: break-all;
  background: var(--bg-hover);
  padding: 6px 10px;
  border-radius: var(--radius-sm);
}
.form-item-top {
  align-items: flex-start !important;
}
.adv-filter-panel {
  position: absolute; top: 100%; left: 0; z-index: 2000;
  background: #fff; border: 1px solid var(--border-color); border-radius: var(--radius-md);
  padding: 15px; box-shadow: var(--shadow-md); width: 340px;
}
</style>