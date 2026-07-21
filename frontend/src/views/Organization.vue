<template>
  <div class="organization-page">
    <div class="top-bar">
      <h3>部门管理</h3>
      <el-button v-if="userInfo.admin" type="primary" @click="showAddDeptDialog()">
        + 新建部门
      </el-button>
    </div>

    <!-- 部门卡片列表 -->
    <div class="dept-cards">
      <div
        v-for="dept in deptList"
        :key="dept.id"
        class="dept-card"
        :class="{ active: selectedDept?.id === dept.id }"
        @click="onDeptClick(dept)"
      >
        <div class="dept-name">{{ dept.name }}</div>
        <div class="dept-meta">
          <span v-if="dept.headUserName">主管: {{ dept.headUserName }}</span>
          <span class="member-count">{{ memberCount[dept.id] || 0 }}人</span>
        </div>
        <div v-if="userInfo.admin" class="dept-actions" @click.stop>
          <el-button size="small" text type="primary" @click="showEditDeptDialog(dept)">编辑</el-button>
          <el-popconfirm title="确定删除该部门？" width="200" @confirm="deleteDept(dept.id)">
            <template #reference>
              <el-button size="small" text type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </div>
      </div>
    </div>

    <!-- 选中部门的成员列表 -->
    <div v-if="selectedDept" class="member-section">
      <div class="member-header">
        <h4>{{ selectedDept.name }} — 成员管理</h4>
        <el-button v-if="userInfo.admin" type="primary" size="small" @click="showAddMemberDialog">添加成员</el-button>
      </div>
      <el-table :data="members" border stripe>
        <el-table-column prop="nickName" label="姓名" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column label="角色">
          <template #default="{ row }">
            <el-tag v-if="row.isAdmin" type="danger">管理员</el-tag>
            <el-tag v-else-if="selectedDept.headUserId === row.userId" type="warning">部门主管</el-tag>
            <el-tag v-else type="info">成员</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="userInfo.admin" label="操作" width="260">
          <template #default="{ row }">
            <el-button v-if="selectedDept.headUserId !== row.userId"
              size="small" @click="setHead(row.userId)">设为主管</el-button>
            <el-button v-else
              size="small" type="warning" @click="cancelHead(row.userId)">取消主管</el-button>
            <el-button size="small" type="danger" @click="removeMember(row.userId)">移出</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新建/编辑部门弹窗 -->
    <el-dialog :title="editingDept ? '编辑部门' : '新建部门'" v-model="deptDialogVisible" width="400px">
      <el-form :model="deptForm">
        <el-form-item label="部门名称">
          <el-input v-model="deptForm.name" placeholder="如：技术部" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deptDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDept">保存</el-button>
      </template>
    </el-dialog>

    <!-- 添加成员弹窗 -->
    <el-dialog title="添加成员" v-model="addMemberVisible" width="500px">
      <el-input v-model="searchKey" placeholder="输入姓名或邮箱搜索" clearable @keyup.enter="searchUsers" />
      <el-table :data="userList" border stripe style="margin-top:12px">
        <el-table-column prop="nickName" label="姓名" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="departmentId" label="所属部门" width="120">
          <template #default="{ row }">
            <template v-if="row.departmentId">{{ deptNameMap[row.departmentId] || row.departmentId }}</template>
            <span v-else style="color:var(--text-tertiary)">无</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button size="small" type="primary" :disabled="!!row.departmentId"
              @click.stop="addMember(row)">加入</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, getCurrentInstance, computed } from "vue";
const { proxy } = getCurrentInstance();
const userInfo = proxy.VueCookies.get("userInfo");

const deptList = ref([]);
const selectedDept = ref(null);
const members = ref([]);
const memberCount = reactive({});
const deptNameMap = reactive({});

const deptDialogVisible = ref(false);
const editingDept = ref(null);
const deptForm = reactive({ name: "" });

const addMemberVisible = ref(false);
const searchKey = ref("");
const userList = ref([]);

const api = {
  list: "/admin/department/list",
  create: "/admin/department/create",
  update: "/admin/department/update",
  delete: "/admin/department/delete",
  setHead: "/admin/department/setHeadUser",
  members: "/admin/departmentMembers",
  assignDept: "/admin/assignDepartment",
  loadUsers: "/admin/loadUserList",
};

onMounted(() => loadDeptList());

async function loadDeptList() {
  const res = await proxy.Request({ url: api.list });
  if (res && res.data) {
    deptList.value = res.data;
    for (const d of res.data) deptNameMap[d.id] = d.name;
    for (const d of res.data) {
      const mr = await proxy.Request({ url: api.members, params: { departmentId: d.id } });
      memberCount[d.id] = mr?.data?.length || 0;
    }
  }
}

async function onDeptClick(data) {
  selectedDept.value = data;
  const res = await proxy.Request({ url: api.members, params: { departmentId: data.id } });
  members.value = res?.data || [];
}

async function setHead(userId) {
  await proxy.Request({ url: api.setHead, params: { id: selectedDept.value.id, headUserId: userId } });
  proxy.Message.success("已设置主管");
  selectedDept.value.headUserId = userId;
  selectedDept.value.headUserName = members.value.find(m => m.userId === userId)?.nickName;
  onDeptClick(selectedDept.value);
  loadDeptList();
}

async function cancelHead(userId) {
  await proxy.Request({ url: api.setHead, params: { id: selectedDept.value.id, headUserId: "" } });
  proxy.Message.success("已取消主管");
  selectedDept.value.headUserId = null;
  selectedDept.value.headUserName = null;
  onDeptClick(selectedDept.value);
  loadDeptList();
}

async function removeMember(userId) {
  if (selectedDept.value.headUserId === userId) {
    await proxy.Request({ url: api.setHead, params: { id: selectedDept.value.id, headUserId: "" } });
    selectedDept.value.headUserId = null;
    selectedDept.value.headUserName = null;
  }
  await proxy.Request({ url: api.assignDept, params: { userId, departmentId: "" } });
  proxy.Message.success("已移出部门");
  onDeptClick(selectedDept.value);
  loadDeptList();
}

async function addMember(row) {
  await proxy.Request({ url: api.assignDept, params: { userId: row.userId, departmentId: selectedDept.value.id } });
  proxy.Message.success("已加入部门");
  addMemberVisible.value = false;
  searchKey.value = "";
  onDeptClick(selectedDept.value);
  loadDeptList();
}

async function searchUsers() {
  const res = await proxy.Request({
    url: api.loadUsers,
    params: { keyword: searchKey.value || undefined, pageSize: 50 }
  });
  userList.value = res?.data?.list || [];
}

function showAddMemberDialog() {
  addMemberVisible.value = true;
  searchKey.value = "";
  searchUsers();
}

function showAddDeptDialog() {
  editingDept.value = null;
  deptForm.name = "";
  deptDialogVisible.value = true;
}

function showEditDeptDialog(dept) {
  editingDept.value = dept;
  deptForm.name = dept.name;
  deptDialogVisible.value = true;
}

async function deleteDept(id) {
  await proxy.Request({ url: api.delete, params: { id } });
  proxy.Message.success("已删除");
  selectedDept.value = null;
  members.value = [];
  loadDeptList();
}

async function saveDept() {
  if (!deptForm.name) { proxy.Message.warning("请输入部门名称"); return; }
  const body = { name: deptForm.name };
  if (editingDept.value) {
    body.id = editingDept.value.id;
    await proxy.Request({ url: api.update, data: body, dataType: 'json' });
  } else {
    await proxy.Request({ url: api.create, data: body, dataType: 'json' });
  }
  deptDialogVisible.value = false;
  proxy.Message.success("保存成功");
  loadDeptList();
}
</script>

<style scoped>
.organization-page { padding: 20px; }
.top-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.dept-cards { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 20px; }
.dept-card {
  padding: 16px 20px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  cursor: pointer;
  min-width: 180px;
  transition: box-shadow var(--transition-fast);
  animation: fadeInUp 0.35s ease both;
  &:nth-child(1) { animation-delay: 0.05s; }
  &:nth-child(2) { animation-delay: 0.1s; }
  &:nth-child(3) { animation-delay: 0.15s; }
  &:nth-child(4) { animation-delay: 0.2s; }
  &:nth-child(5) { animation-delay: 0.25s; }
  &:nth-child(6) { animation-delay: 0.3s; }
}
.dept-card:hover, .dept-card.active { border-color: var(--primary); background: var(--bg-active); box-shadow: var(--shadow-sm); }
.dept-name { font-size: 15px; font-weight: bold; margin-bottom: 8px; }
.dept-meta { font-size: 12px; color: var(--text-tertiary); display: flex; gap: 12px; }
.member-section { margin-top: 30px; }
.member-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.member-section :deep(.el-tag) { transition: none; }
</style>
