<template>
  <div class="dashboard">
    <h1>Dashboard</h1>
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>Total Users</template>
          <div class="card-value">1,234</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>Active Sessions</template>
          <div class="card-value">56</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>System Status</template>
          <div class="card-value green">Healthy</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.dashboard {
  padding: 20px;
}
.card-value {
  font-size: 24px;
  font-weight: bold;
}
.green {
  color: #67c23a;
}
</style>
