<template>
  <div style="display: flex; align-items: center; justify-content: center; height: 100%;">
    <n-result status="404" title="Page Not Found" description="The page you are looking for does not exist.">
      <template #footer>
        <n-button @click="$router.push('/')">Go Home</n-button>
      </template>
    </n-result>
  </div>
</template>
