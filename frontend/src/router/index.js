import { createRouter, createWebHistory } from 'vue-router'
import VueCookies from 'vue-cookies'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: '登录',
      component: () => import("@/views/Login.vue")
    },
    {
      path: "/",
      component: () => import("@/views/Framework.vue"),
      children: [
        {
          path: '/',
          redirect: "/dashboard"
        },
        {
          path: '/dashboard',
          name: '仪表盘',
          meta: {
            needLogin: true,
            menuCode: "dashboard"
          },
          component: () => import("@/views/Dashboard.vue")
        },
        {
          path: '/main/:category',
          name: '首页',
          meta: {
            needLogin: true,
            menuCode: "main"
          },
          component: () => import("@/views/main/Main.vue")
        },
        {
          path: '/myshare',
          name: '我的分享',
          meta: {
            needLogin: true,
            menuCode: "share"
          },
          component: () => import("@/views/share/Share.vue")
        },
        {
          path: '/recycle',
          name: '回收站',
          meta: {
            needLogin: true,
            menuCode: "recycle"
          },
          component: () => import("@/views/recycle/Recycle.vue")
        },
        {
          path: '/settings/sysSetting',
          name: '系统设置',
          meta: {
            needLogin: true,
            menuCode: "settings"
          },
          component: () => import("@/views/admin/SysSettings.vue")
        },
        {
          path: '/settings/userList',
          name: '用户管理',
          meta: {
            needLogin: true,
            menuCode: "settings"
          },
          component: () => import("@/views/admin/UserList.vue")
        },
        {
          path: '/settings/fileList',
          name: '用户文件',
          meta: {
            needLogin: true,
            menuCode: "settings"
          },
          component: () => import("@/views/admin/FileList.vue")
        },
        {
          path: '/organization',
          name: '部门管理',
          meta: {
            needLogin: true,
            menuCode: "organization"
          },
          component: () => import("@/views/Organization.vue")
        },
        {
          path: '/audit',
          name: '操作审计',
          meta: {
            needLogin: true,
            menuCode: "audit"
          },
          component: () => import("@/views/Audit.vue")
        },
        {
          path: '/archive',
          name: '归档库',
          meta: {
            needLogin: true,
            menuCode: "archive"
          },
          component: () => import("@/views/Archive.vue")
        },
        {
          path: '/approval',
          name: '审批管理',
          meta: {
            needLogin: true,
            menuCode: "approval"
          },
          component: () => import("@/views/Approval.vue")
        },
      ]
    },
    {
      path: '/shareCheck/:shareId',
      name: '分享校验',
      component: () => import("@/views/webshare/ShareCheck.vue")
    },
    {
      path: '/share/:shareId',
      name: '分享',
      component: () => import("@/views/webshare/Share.vue")
    }
  ]
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem("accessToken");
  if (to.meta.needLogin != null && to.meta.needLogin && token == null) {
    router.push("/login");
  }
  next();
})

export default router
