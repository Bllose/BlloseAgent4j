<template>
  <n-layout has-sider style="height: 100vh">
    <SideMenu />
    <n-layout>
      <TopBar />
      <n-layout-content style="padding: 16px; height: calc(100vh - 46px); overflow: auto;">
        <router-view />
      </n-layout-content>
    </n-layout>
  </n-layout>
</template>

<script setup>
import SideMenu from '../components/SideMenu.vue'
import TopBar from '../components/TopBar.vue'
</script>
