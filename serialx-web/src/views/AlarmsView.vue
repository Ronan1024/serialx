<template>
  <div class="alarms-page">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">告警中心</h1>
        <p class="page-subtitle">配置告警策略、管理通知渠道及查看历史告警记录</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :icon="Plus">新建策略</el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="当前活动告警" name="active">
        <div class="active-alarms mt-16">
          <el-alert
            v-for="a in activeAlarms"
            :key="a.id"
            :title="a.title"
            :type="a.level"
            :description="a.desc"
            show-icon
            class="mb-16"
          >
            <template #default>
              <div class="alert-footer">
                <span>发生时间: {{ a.time }}</span>
                <el-button link type="primary">标记已读</el-button>
              </div>
            </template>
          </el-alert>
        </div>
      </el-tab-pane>
      
      <el-tab-pane label="告警策略配置" name="rules">
        <el-table :data="rules" class="mt-16">
          <el-table-column prop="name" label="策略名称" />
          <el-table-column prop="condition" label="触发条件" min-width="200" />
          <el-table-column prop="channel" label="通知渠道" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-switch v-model="row.enabled" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default>
              <el-button link type="primary">编辑</el-button>
              <el-button link type="danger">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'

const activeTab = ref('active')
const activeAlarms = [
  { id: 1, title: 'Snowflake WorkerID 冲突风险', level: 'error', desc: '检测到节点 10.0.1.15 与 10.0.1.18 上报了相同的 WorkerID: 5', time: '2026-05-14 12:05:10' },
  { id: 2, title: 'Segment 号段预取失败', level: 'warning', desc: 'Namespace [order_id] 在节点 sx-service-001 上连续 3 次尝试从数据库预取号段失败', time: '2026-05-14 11:45:22' },
]
const rules = ref([
  { name: '发号失败率告警', condition: '5分钟内错误率 > 1%', channel: '钉钉, 邮件', enabled: true },
  { name: '时钟回拨异常告警', condition: '发生时钟回拨 > 10ms', channel: '企业微信', enabled: true },
  { name: '号段余额不足', condition: 'Segment 剩余量 < 20%', channel: '短信', enabled: false },
])
</script>

<style scoped>
.page-header { margin-bottom: 24px; }
.page-title { margin: 0 0 4px; font-size: 22px; font-weight: 700; }
.page-subtitle { margin: 0; font-size: 13px; color: var(--text-secondary); }
.mb-16 { margin-bottom: 16px; }
.alert-footer { display: flex; justify-content: space-between; align-items: center; margin-top: 8px; font-size: 12px; }
</style>
