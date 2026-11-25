<template>
  <div>
    <!-- ruleid: sentinel-vue-v-html -->
    <p v-html="echoValue"/>
    <!-- ruleid: sentinel-vue-v-html -->
    {{msg.id}}::<span v-html="msg.content"></span>
    <!-- ok: sentinel-vue-v-html -->
    <p>{{ echoValue }}</p>
    <!-- ok: sentinel-vue-v-html -->
    <p v-html="DOMPurify.sanitize(echoValue)"></p>
    <!-- ok: sentinel-vue-v-html -->
    <p v-html="sanitizar(msg.content)"></p>
    <!-- ok: sentinel-vue-v-html -->
<!--    <p v-html="page"/>-->
  </div>
</template>
