<template>
  <n-layout has-sider style="height: 100vh">
    <SideMenu />
    <n-layout>
      <TopBar />
      <n-layout-content style="padding: 16px; height: calc(100vh - 46px - 32px); overflow: auto;">
        <router-view />
      </n-layout-content>
      <footer class="footer-bar">
        <span>
          <a href="http://beian.miit.gov.cn" target="_blank">粤ICP备2023158794号</a>
        </span>
        <span>邮箱: Bllose@qq.com</span>
        <span>V: Rcedw2019</span>
        <span>
          <a href="https://github.com/Bllose/BlloseAgent4j" target="_blank">GitHub</a>
        </span>
        <span>
          <a href="https://gitee.com/bllose/bllose-agent4j" target="_blank">Gitee</a>
        </span>
      </footer>
    </n-layout>
  </n-layout>
</template>

<script setup>
import SideMenu from '../components/SideMenu.vue'
import TopBar from '../components/TopBar.vue'
</script>

<style scoped>
.footer-bar {
  font-family: SimSun, 宋体, serif;
  color: #888;
  font-size: 13px;
  text-align: center;
  line-height: 32px;
  height: 32px;
  flex-shrink: 0;
}
.footer-bar span {
  margin: 0 8px;
}
.footer-bar a {
  color: #888;
  text-decoration: none;
}
.footer-bar a:hover {
  color: #555;
  text-decoration: underline;
}
</style>
