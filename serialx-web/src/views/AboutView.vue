<template>
  <div class="settings-page">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">系统设置</h1>
        <p class="page-subtitle">配置全局发号参数、监控基础设施状态及查看系统信息</p>
      </div>
    </div>

    <el-row :gutter="24">
      <!-- System Config -->
      <el-col :span="14">
        <el-card shadow="never" class="settings-card">
          <template #header>
            <div class="card-header">
              <el-icon><Setting /></el-icon>
              <span class="fw-700">全局基础配置</span>
            </div>
          </template>
          
          <el-form label-position="top">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="服务刷新频率 (Refresh Interval)">
                  <el-input value="5,000" disabled>
                    <template #append>ms</template>
                  </el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="实例上报频率 (Report Interval)">
                  <el-input value="10,000" disabled>
                    <template #append>ms</template>
                  </el-input>
                </el-form-item>
              </el-col>
            </el-row>
            
            <el-form-item label="Redis 通知频道 (Pub/Sub Channel)">
              <el-input value="sx_namespace_change_channel" disabled class="mono" />
            </el-form-item>

            <el-divider>Snowflake 全局位宽</el-divider>
            
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="时间戳 (Bit)">
                  <el-input-number :model-value="41" disabled controls-position="right" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="机器 ID (Bit)">
                  <el-input-number :model-value="10" disabled controls-position="right" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="序列号 (Bit)">
                  <el-input-number :model-value="12" disabled controls-position="right" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            
            <div class="settings-info-box">
              <el-icon><InfoFilled /></el-icon>
              <span>以上参数为系统引导启动时加载，如需修改请更新 <code>application.yml</code> 并重启服务集群。</span>
            </div>
          </el-form>
        </el-card>

        <el-card shadow="never" class="mt-20">
          <template #header>
            <div class="card-header">
              <el-icon><Lock /></el-icon>
              <span class="fw-700">安全与令牌策略</span>
            </div>
          </template>
          <div class="security-list">
            <div class="security-item">
              <div class="sec-info">
                <div class="sec-title">JWT 令牌有效期</div>
                <div class="sec-desc">当前会话 Token 签发有效时长</div>
              </div>
              <div class="sec-val">24 小时</div>
            </div>
            <div class="security-item">
              <div class="sec-info">
                <div class="sec-title">最大登录失败次数</div>
                <div class="sec-desc">连续失败后锁定账号 30 分钟</div>
              </div>
              <div class="sec-val">5 次</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- Infrastructure Status -->
      <el-col :span="10">
        <el-card shadow="never" class="settings-card">
          <template #header>
            <div class="card-header">
              <el-icon><Monitor /></el-icon>
              <span class="fw-700">基础设施运行状态</span>
            </div>
          </template>
          
          <div class="infra-list">
            <div class="infra-item">
              <div class="infra-icon mysql">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 4.02 2 6.5s4.48 4.5 10 4.5 10-2.02 10-4.5S17.52 2 12 2zm0 18c-5.52 0-10-2.02-10-4.5v2c0 2.48 4.48 4.5 10 4.5s10-2.02 10-4.5v-2c0 2.48-4.48 4.5-10 4.5zm0-4.5c-5.52 0-10-2.02-10-4.5v2c0 2.48 4.48 4.5 10 4.5s10-2.02 10-4.5v-2c0 2.48-4.48 4.5-10 4.5z"/></svg>
              </div>
              <div class="infra-info">
                <div class="infra-name">MySQL 8.0 Primary</div>
                <div class="infra-host">10.0.1.10:3306</div>
              </div>
              <div class="infra-status online">CONNECTED</div>
            </div>

            <div class="infra-item">
              <div class="infra-icon redis">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5-10-5-10 5zM2 12l10 5 10-5-10-5-10 5z"/></svg>
              </div>
              <div class="infra-info">
                <div class="infra-name">Redis 7.2 Cluster</div>
                <div class="infra-host">10.0.1.11:6379</div>
              </div>
              <div class="infra-status online">CONNECTED</div>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="mt-20 version-card">
          <div class="version-content">
            <div class="logo-area">
               <svg width="40" height="40" viewBox="0 0 28 28" fill="none">
                <rect width="28" height="28" rx="8" fill="url(#set-grad)" />
                <path d="M8 14h5l2-5 2 10 2-5h1" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                <defs>
                  <linearGradient id="set-grad" x1="0" y1="0" x2="28" y2="28">
                    <stop offset="0%" stop-color="#2563eb"/>
                    <stop offset="100%" stop-color="#0ea5e9"/>
                  </linearGradient>
                </defs>
              </svg>
            </div>
            <div class="version-info">
              <div class="v-name">SerialX Governance Console</div>
              <div class="v-num">Version 1.0.8-RELEASE</div>
              <div class="v-copy">© 2026 SerialX Team</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { Setting, Monitor, InfoFilled, Lock } from '@element-plus/icons-vue'
</script>

<style scoped>
.page-header { margin-bottom: 24px; }
.page-title { margin: 0 0 4px; font-size: 22px; font-weight: 700; }
.page-subtitle { margin: 0; font-size: 13px; color: var(--text-secondary); }

.card-header { display: flex; align-items: center; gap: 8px; }
.card-header .el-icon { color: var(--accent); }

.settings-info-box {
  margin-top: 24px;
  padding: 12px 16px;
  background: var(--bg-card-alt);
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--text-secondary);
}
.settings-info-box code { color: var(--accent); font-weight: 700; }

.security-item { display: flex; justify-content: space-between; align-items: center; padding: 12px 0; }
.sec-title { font-size: 14px; font-weight: 600; }
.sec-desc { font-size: 11px; color: var(--text-muted); }
.sec-val { font-size: 14px; font-weight: 700; color: var(--text-secondary); }

.infra-item { display: flex; align-items: center; gap: 16px; padding: 16px 0; border-bottom: 1px solid var(--border-light); }
.infra-item:last-child { border-bottom: none; }
.infra-icon { width: 40px; height: 40px; border-radius: 8px; display: flex; align-items: center; justify-content: center; }
.infra-icon.mysql { background: #e0f2fe; color: #0284c7; }
.infra-icon.redis { background: #fee2e2; color: #dc2626; }
.infra-name { font-size: 14px; font-weight: 600; }
.infra-host { font-size: 11px; color: var(--text-muted); }
.infra-status { margin-left: auto; font-size: 11px; font-weight: 700; }
.infra-status.online { color: var(--green); }

.version-card { background: var(--sidebar-bg); border: none !important; color: #fff; }
.version-content { display: flex; align-items: center; gap: 16px; }
.v-name { font-weight: 700; font-size: 15px; }
.v-num { font-size: 12px; color: rgba(255,255,255,0.4); }
.v-copy { font-size: 11px; color: rgba(255,255,255,0.2); margin-top: 4px; }
</style>
