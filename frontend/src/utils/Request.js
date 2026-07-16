import axios from 'axios'

import { ElLoading } from 'element-plus'
import router from '@/router'

import Message from '../utils/Message'

const contentTypeForm = 'application/x-www-form-urlencoded;charset=UTF-8'
const contentTypeJson = 'application/json'
//arraybuffer	ArrayBuffer对象
//blob	Blob对象
const responseTypeJson = "json"

let loading = null;
const instance = axios.create({
    baseURL: '/api',
    timeout: 30 * 1000,
});
//请求前拦截器
instance.interceptors.request.use(
    (config) => {
        if (config.showLoading) {
            loading = ElLoading.service({
                lock: true,
                text: '加载中......',
                background: 'rgba(0, 0, 0, 0.7)',
            });
        }
        // JWT Token 拦截器
        const token = localStorage.getItem('accessToken');
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        if (config.showLoading && loading) {
            loading.close();
        }
        Message.error("请求发送失败");
        return Promise.reject("请求发送失败");
    }
);
//请求后拦截器
let isRefreshing = false;
let pendingRequests = [];

function flushPendingRequests(token) {
    pendingRequests.forEach(cb => cb(token));
    pendingRequests = [];
}

function clearAuthAndGoLogin(target) {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("refreshToken");
    router.push(target || "/login");
}

instance.interceptors.response.use(
    (response) => {
        const { showLoading, errorCallback, showError = true, responseType } = response.config;
        if (showLoading && loading) {
            loading.close()
        }
        const responseData = response.data;
        if (responseType == "arraybuffer" || responseType == "blob") {
            return responseData;
        }
        //正常请求
        if (responseData.code == 200) {
            return responseData;
        } else if (responseData.code == 901) {
            //登录超时
            router.push("/login?redirectUrl=" + encodeURI(router.currentRoute.value.path));
            return Promise.reject({ showError: false, msg: "登录超时" });
        } else {
            //其他错误
            if (errorCallback) {
                errorCallback(responseData.info);
            }
            return Promise.reject({ showError: showError, msg: responseData.info });
        }
    },
    async (error) => {
        if (error.config?.showLoading && loading) {
            loading.close();
        }
        // 401: accessToken 过期，尝试用 refreshToken 无感续期
        if (error.response?.status === 401) {
            // 被挤下线：跳过 refresh 直接回登录页
            if (error.response?.data?.code === 902) {
                clearAuthAndGoLogin("/login?kicked=1");
                return Promise.reject({ showError: false, msg: "账号已在别处登录" });
            }
            const refreshToken = localStorage.getItem("refreshToken");
            // 未重试过且有 refreshToken → 走续期
            if (!error.config._retry && refreshToken) {
                if (!isRefreshing) {
                    isRefreshing = true;
                    try {
                        const refreshRes = await instance.post("/auth/refresh",
                            new URLSearchParams({ refreshToken }),
                            { headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" } }
                        );
                        if (refreshRes.code === 200 && refreshRes.data?.accessToken) {
                            const newToken = refreshRes.data.accessToken;
                            localStorage.setItem("accessToken", newToken);
                            flushPendingRequests(newToken);
                            // 重试原请求
                            error.config.headers.Authorization = `Bearer ${newToken}`;
                            error.config._retry = true;
                            return instance(error.config);
                        }
                    } catch (e) {
                        // refresh 也失败了
                    } finally {
                        isRefreshing = false;
                    }
                    // refresh 失败：唤醒排队请求（传 null → 各自失败返回），不能让它们永久挂起
                    flushPendingRequests(null);
                } else {
                    return new Promise((resolve, reject) => {
                        pendingRequests.push(token => {
                            if (token) {
                                error.config.headers.Authorization = `Bearer ${token}`;
                                error.config._retry = true;
                                resolve(instance(error.config));
                            } else {
                                reject({ showError: false, msg: "登录已过期" });
                            }
                        });
                    });
                }
            }
            // 到这里：无 refreshToken / refresh 失败 / 重试后仍 401 → 统一清 token 回登录页
            clearAuthAndGoLogin();
            return Promise.reject({ showError: false, msg: "登录已过期" });
        }
        return Promise.reject({ showError: true, msg: "网络异常" })
    }
);

const request = (config) => {
    const { url, params, data, dataType, showLoading = true, responseType = responseTypeJson } = config;
    let contentType = contentTypeForm;
    let requestData;

    if (dataType === 'json' && data) {
        contentType = contentTypeJson;
        requestData = JSON.stringify(data);
    } else {
        let formData = new FormData();
        for (let key in params) {
            formData.append(key, params[key] == undefined ? "" : params[key]);
        }
        if (data) {
            for (let key in data) {
                formData.append(key, data[key] == undefined ? "" : data[key]);
            }
        }
        requestData = formData;
    }

    let headers = {
        'Content-Type': contentType,
        'X-Requested-With': 'XMLHttpRequest',
    }

    return instance.post(url, requestData, {
        onUploadProgress: (event) => {
            if (config.uploadProgressCallback) {
                config.uploadProgressCallback(event);
            }
        },
        responseType: responseType,
        headers: headers,
        showLoading: showLoading,
        errorCallback: config.errorCallback,
        showError: config.showError
    }).catch(error => {
        console.log(error);
        if (error.showError) {
            Message.error(error.msg);
        }
        return null;
    });
};

export default request;
