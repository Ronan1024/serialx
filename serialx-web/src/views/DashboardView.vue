<template>
  <div class="dashboard-page fade-in">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">概览仪表盘</h1>
        <p class="page-subtitle">实时监控全网发号吞吐、系统健康度及资源分布情况</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Refresh" @click="refreshDashboard" round>刷新数据</el-button>
      </div>
    </div>

    <div class="dashboard-grid">
      <!-- Stats Grid -->
      <div class="stats-grid">
        <div class="bento-card stat-card" v-for="stat in dashboardStats" :key="stat.label">
          <div class="stat-header">
            <span class="stat-label">{{ stat.label }}</span>
            <el-icon :class="['stat-icon', stat.type]"><component :is="stat.icon" /></el-icon>
          </div>
          <div class="stat-body">
            <div class="stat-value">{{ stat.value }}</div>
            <div class="stat-trend" :class="stat.trend > 0 ? 'up' : 'down'">
              <el-icon><component :is="stat.trend > 0 ? 'CaretTop' : 'CaretBottom'" /></el-icon>
              {{ Math.abs(stat.trend) }}%
              <span class="trend-text">较昨日</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Main Content Grid -->
      <div class="main-content">
        <!-- Left Column -->
        <div class="left-col">
          <div class="bento-card chart-card">
            <div class="card-header">
              <span class="fw-700 text-lg">全网发号吞吐趋势 (24h)</span>
              <div class="chart-legend">
                <span class="legend-item"><i class="dot blue"></i> 成功数</span>
              </div>
            </div>
            <div class="simple-chart">
              <div v-for="(val, idx) in chartBars" :key="idx" class="simple-bar-item">
                <div class="bar-num">{{ val }}k</div>
                <div class="bar-track">
                  <div class="bar-fill" :style="{ height: val + '%' }"></div>
                </div>
                <div class="bar-time">{{ idx * 2 }}:00</div>
              </div>
            </div>
          </div>

          <div class="split-col">
            <div class="bento-card">
              <div class="card-header mb-16"><span class="fw-700 text-lg">待办任务</span></div>
              <div class="todo-list">
                <div class="todo-item" v-for="todo in todos" :key="todo.id" @click="router.push(todo.link)">
                  <div class="todo-icon-outline" :class="todo.type">
                    <el-icon><component :is="todo.icon" /></el-icon>
                  </div>
                  <div class="todo-content">
                    <div class="todo-title">{{ todo.title }}</div>
                    <div class="todo-time">{{ todo.time }}</div>
                  </div>
                  <el-icon class="todo-arrow text-muted"><ArrowRight /></el-icon>
                </div>
              </div>
            </div>
            
            <div class="bento-card">
              <div class="card-header mb-16"><span class="fw-700 text-lg">系统健康度</span></div>
              <div class="health-list">
                <div class="health-item" v-for="h in health" :key="h.name">
                  <div class="health-info">
                    <span class="h-name">{{ h.name }}</span>
                    <span class="h-val mono">{{ h.val }}%</span>
                  </div>
                  <el-progress :percentage="h.val" :color="h.color" :show-text="false" :stroke-width="8" />
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Right Column -->
        <div class="right-col">
          <div class="bento-card">
            <div class="card-header mb-16"><span class="fw-700 text-lg">节点分布 (Geo)</span></div>
            <div class="geo-dist">
              <div v-for="g in geo" :key="g.region" class="geo-item">
                <div class="geo-info">
                  <span class="geo-region">{{ g.region }}</span>
                  <span class="geo-val mono">{{ g.count }} 节点</span>
                </div>
                <div class="geo-bar-bg">
                  <div class="geo-bar-fill" :style="{ width: g.percent + '%' }"></div>
                </div>
              </div>
            </div>
          </div>

          <div class="bento-card logs-card">
            <div class="card-header mb-16">
              <span class="fw-700 text-lg">最新审计</span>
              <el-button link type="primary" @click="router.push('/audit')">查看全部</el-button>
            </div>
            <div class="recent-logs">
              <div class="recent-log-item" v-for="log in recentLogs" :key="log.id">
                <div class="log-timeline-dot"></div>
                <div class="log-info">
                  <div class="log-msg">
                    <span class="log-user">{{ log.user }}</span>
                    <span class="log-action">{{ log.action }}</span>
                    <span class="log-target mono">{{ log.target }}</span>
                  </div>
                  <div class="log-time">{{ log.time }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { 
  Refresh, DataLine, Monitor, Connection, Timer,
  CaretTop, CaretBottom, DocumentChecked, Bell, Warning, ArrowRight
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()

const dashboardStats = [
  { label: '今日发号总量', value: '2.48M', icon: DataLine, type: 'primary', trend: 12.5 },
  { label: '活跃服务实例', value: '18', icon: Monitor, type: 'success', trend: 5.2 },
  { label: '已授权应用', value: '128', icon: Connection, type: 'warning', trend: 2.1 },
  { label: '平均 P99 延迟', value: '1.2ms', icon: Timer, type: 'danger', trend: -0.8 },
]

// 12 bars for a simpler view (every 2 hours)
const chartBars = ref(Array.from({ length: 12 }, () => Math.floor(Math.random() * 70) + 10))

const todos = [
  { id: 1, title: '待审批发布申请', time: '10分钟前', icon: DocumentChecked, type: 'primary', link: '/approvals' },
  { id: 2, title: 'Snowflake 租约将过期', time: '1小时前', icon: Warning, type: 'danger', link: '/nodes' },
  { id: 3, title: '新应用接入待审核', time: '3小时前', icon: Bell, type: 'warning', link: '/approvals' },
]

const health = [
  { name: '控制面 (Admin)', val: 100, color: 'var(--green)' },
  { name: '数据面 (Service)', val: 95, color: 'var(--accent)' },
  { name: 'Redis 存储', val: 100, color: 'var(--green)' },
  { name: 'MySQL 持久化', val: 98, color: 'var(--green)' },
]

const geo = [
  { region: '华东 (上海)', count: 8, percent: 45 },
  { region: '华北 (北京)', count: 6, percent: 35 },
  { region: '华南 (深圳)', count: 4, percent: 20 },
]

const recentLogs = [
  { id: 1, user: 'admin', action: '发布了', target: 'order_id', time: '5分钟前' },
  { id: 2, user: 'ronan', action: '回滚了', target: 'user_sn', time: '18分钟前' },
  { id: 3, user: 'jiang', action: '注册了应用', target: 'payment_gw', time: '45分钟前' },
  { id: 4, user: 'system', action: '回收了', target: 'worker_3', time: '1小时前' },
]

const refreshDashboard = () => {
  chartBars.value = Array.from({ length: 12 }, () => Math.floor(Math.random() * 70) + 10)
  ElMessage.success('全网监控数据已同步')
}
</script>

<style scoped>
.dashboard-page {
  max-width: 1600px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.page-title {
  margin: 0 0 4px;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: -0.03em;
  color: var(--text-primary);
}

.page-subtitle {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

/* Base Bento Card */
.bento-card {
  background: #ffffff;
  border-radius: 20px;
  padding: 24px;
  border: 1px solid rgba(0,0,0,0.04);
  box-shadow: 0 4px 20px rgba(0,0,0,0.02);
  display: flex;
  flex-direction: column;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.text-lg { font-size: 16px; }
.mb-16 { margin-bottom: 16px; }

/* Grid Layouts (Ensures perfect 24px spacing) */
.dashboard-grid {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

.main-content {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 24px;
  align-items: start;
}

.left-col {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.split-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

.right-col {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* Stats Cards */
.stat-card {
  padding: 20px 24px;
}
.stat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.stat-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
}
.stat-icon {
  font-size: 20px;
  padding: 8px;
  border-radius: 12px;
  background: transparent;
}
.stat-icon.primary { color: var(--accent); border: 1px solid var(--accent-soft); }
.stat-icon.success { color: var(--green); border: 1px solid rgba(16,185,129,0.2); }
.stat-icon.warning { color: var(--amber); border: 1px solid rgba(245,158,11,0.2); }
.stat-icon.danger  { color: var(--red); border: 1px solid rgba(239,68,68,0.2); }

.stat-value {
  font-size: 32px;
  font-weight: 800;
  color: var(--text-primary);
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.stat-trend {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 600;
  margin-top: 10px;
}
.stat-trend.up { color: var(--green); }
.stat-trend.down { color: var(--red); }
.trend-text { color: var(--text-muted); font-weight: 400; margin-left: 4px; }

/* Simple Chart */
.chart-card {
  min-height: 360px;
}
.chart-legend { display: flex; gap: 16px; }
.legend-item { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--text-secondary); }
.dot { width: 8px; height: 8px; border-radius: 4px; }
.dot.blue { background: var(--accent); }

.simple-chart {
  flex: 1;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-top: 32px;
  padding: 0 10px;
}

.simple-bar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.bar-num {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-primary);
}

.bar-track {
  width: 24px;
  height: 200px;
  background: var(--bg-card-alt);
  border-radius: 6px;
  display: flex;
  align-items: flex-end;
  overflow: hidden;
}

.bar-fill {
  width: 100%;
  background: var(--accent);
  border-radius: 6px;
  transition: height 0.6s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.bar-track:hover .bar-fill {
  background: var(--accent-hover);
}

.bar-time {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 500;
}

/* Todos */
.todo-list { display: flex; flex-direction: column; gap: 12px; }
.todo-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px;
  border-radius: 14px;
  border: 1px solid var(--border-light);
  cursor: pointer;
  transition: all 0.2s;
  background: var(--bg-page);
}
.todo-item:hover {
  background: #ffffff;
  border-color: var(--accent-soft);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}
.todo-icon-outline {
  font-size: 18px;
  padding: 8px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.todo-icon-outline.primary { color: var(--accent); background: var(--accent-soft); }
.todo-icon-outline.danger { color: var(--red); background: rgba(239,68,68,0.08); }
.todo-icon-outline.warning { color: var(--amber); background: rgba(245,158,11,0.08); }

.todo-content { flex: 1; }
.todo-title { font-size: 14px; font-weight: 600; color: var(--text-primary); }
.todo-time { font-size: 12px; color: var(--text-muted); margin-top: 4px; }
.todo-arrow { opacity: 0; transition: opacity 0.2s, transform 0.2s; }
.todo-item:hover .todo-arrow { opacity: 1; transform: translateX(4px); }

/* Health */
.health-list { display: flex; flex-direction: column; gap: 24px; padding-top: 8px; }
.health-item { display: flex; flex-direction: column; gap: 10px; }
.health-info { display: flex; justify-content: space-between; }
.h-name { font-size: 13px; font-weight: 600; color: var(--text-secondary); }
.h-val { font-size: 13px; font-weight: 700; color: var(--text-primary); }

/* Geo */
.geo-dist { display: flex; flex-direction: column; gap: 24px; padding-top: 8px; }
.geo-item { display: flex; flex-direction: column; gap: 10px; }
.geo-info { display: flex; justify-content: space-between; align-items: center; }
.geo-region { font-size: 14px; font-weight: 600; color: var(--text-primary); }
.geo-val { font-size: 13px; color: var(--text-muted); }
.geo-bar-bg { width: 100%; height: 8px; background: var(--border-light); border-radius: 4px; overflow: hidden; }
.geo-bar-fill { height: 100%; background: var(--accent); border-radius: 4px; }

/* Logs */
.logs-card { flex: 1; }
.recent-logs { display: flex; flex-direction: column; gap: 20px; padding-left: 6px; }
.recent-log-item { display: flex; gap: 16px; position: relative; }
.recent-log-item::before {
  content: '';
  position: absolute;
  left: 3px;
  top: 14px;
  bottom: -20px;
  width: 2px;
  background: var(--border-light);
}
.recent-log-item:last-child::before { display: none; }

.log-timeline-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fff;
  border: 2px solid var(--accent);
  margin-top: 6px;
  position: relative;
  z-index: 2;
}

.log-info { flex: 1; }
.log-msg { font-size: 13px; color: var(--text-secondary); line-height: 1.5; }
.log-user { font-weight: 600; color: var(--text-primary); }
.log-action { margin: 0 4px; }
.log-target { color: var(--accent); font-weight: 600; padding: 2px 6px; background: var(--accent-soft); border-radius: 4px; }
.log-time { font-size: 11px; color: var(--text-muted); margin-top: 6px; }
</style>
