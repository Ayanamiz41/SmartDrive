<template>
  <div>
    <div class="top">
      <el-button
        type="success"
        :disabled="selectFileIdList.length == 0"
        @click="revertBatch"
      >
        <span class="iconfont icon-revert"></span>还原
      </el-button>
      <el-button
        type="danger"
        :disabled="selectFileIdList.length == 0"
        @click="delBatch"
      >
        <span class="iconfont icon-del"></span>批量删除
      </el-button>
    </div>

    <div class="file-list">
      <Table
        :columns="columns"
        :showPagination="true"
        :dataSource="tableData"
        :fetch="loadDataList"
        :options="tableOptions"
        @rowSelected="rowSelected"
        @sortChange="handleSortChange"
      >
        <template #fileName="{ index, row }">
          <div
            class="file-item"
            @mouseenter="showOp(row)"
            @mouseleave="cancelShowOp(row)"
          >
            <template
              v-if="
                (row.fileType == 3 || row.fileType == 1) && row.status !== 0
              "
            >
              <icon :cover="row.fileCover"></icon>
            </template>
            <template v-else>
              <icon v-if="row.folderType == 0" :fileType="row.fileType"></icon>
              <icon v-if="row.folderType == 1" :fileType="0"></icon>
            </template>
            <span class="file-name" :title="row.fileName">
              <span>{{ row.fileName }}</span>
            </span>
            <span class="op" v-if="row.showOp && row.fileId">
              <span class="iconfont icon-revert" @click="revert(row)"
                >还原</span
              >
              <span class="iconfont icon-del" @click="delFile(row)"
                >删除</span
              >
            </span>
          </div>
        </template>
        <template #fileSize="{ index, row }">
          <span v-if="row.fileSize">
            {{ proxy.Utils.size2Str(row.fileSize) }}</span
          >
        </template>
      </Table>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, getCurrentInstance, nextTick } from "vue";
import { useRouter, useRoute } from "vue-router";
const { proxy } = getCurrentInstance();
const router = useRouter();
const route = useRoute();

const api = {
  loadDataList: "/recycle/loadRecycleList",
  delFile: "/recycle/delFile",
  recoverFile: "/recycle/recoverFile",
};

//列表
const columns = [
  {
    label: "文件名",
    prop: "fileName",
    scopedSlots: "fileName",
    minWidth: 200,
  },
  {
    label: "所属部门",
    prop: "departmentName",
    width: 120,
  },
  {
    label: "删除时间",
    prop: "recycleTime",
    width: 200,
    sortable: true,
  },
  {
    label: "大小",
    prop: "fileSize",
    scopedSlots: "fileSize",
    width: 200,
    sortable: true,
  },
];
//列表
const tableData = ref({});
const tableOptions = {
  extHeight: 20,
  selectType: "checkbox",
};
const sortField = ref(null);
const sortOrder = ref(null);
const handleSortChange = ({ prop, order }) => {
  sortField.value = order ? prop : null;
  sortOrder.value = order;
  tableData.value.pageNo = 1;
  loadDataList();
};
const loadDataList = async () => {
  let params = {
    pageNo: tableData.value.pageNo || 1,
    pageSize: tableData.value.pageSize || 15,
  };
  const SORT_MAP = { recycleTime: 'recycle_time', fileSize: 'file_size' };
  if (sortField.value && sortOrder.value) {
    const dbField = SORT_MAP[sortField.value];
    if (dbField) params.orderBy = dbField + ' ' + (sortOrder.value === 'ascending' ? 'asc' : 'desc');
  }
  if (params.category !== "all") {
    delete params.filePid;
  }
  let result = await proxy.Request({
    url: api.loadDataList,
    params,
  });
  if (!result) {
    return;
  }
  tableData.value = result.data;
};

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

const selectFileIdList = ref([]);
const rowSelected = (rows) => {
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

//恢复
const revert = (row) => {
  checkDuplicate(row.filePid, row.fileName, !!row.departmentId, row.departmentId).then(dup => {
    const msg = dup
      ? "该目录下已经有同名文件，是否继续？"
      : `你确定要还原【${row.fileName}】吗？`;
    proxy.Confirm(msg, async () => {
      let result = await proxy.Request({
        url: api.recoverFile,
        params: {
          fileIds: row.fileId,
        },
      });
      if (!result) {
        return;
      }
      loadDataList();
    });
  });
};

const revertBatch = () => {
  if (selectFileIdList.value.length == 0) {
    return;
  }
  proxy.Confirm(`你确定要还原这些文件吗？`, async () => {
    let result = await proxy.Request({
      url: api.recoverFile,
      params: {
        fileIds: selectFileIdList.value.join(","),
      },
    });
    if (!result) {
      return;
    }
    loadDataList();
  });
};
//删除文件
const emit = defineEmits(["reload"]);
const delFile = (row) => {
  proxy.Confirm(`你确定要删除【${row.fileName}】？`, async () => {
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
    emit("reload");
  });
};

const delBatch = (row) => {
  if (selectFileIdList.value.length == 0) {
    return;
  }
  proxy.Confirm(`你确定要删除选中的文件?删除将无法恢复`, async () => {
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
    emit("reload");
  });
};
</script>

<style lang="scss" scoped>
@import "@/assets/file.list.scss";
.file-list {
  margin-top: 10px;
  .file-item {
    .op {
      width: 120px;
    }
  }
}
</style>