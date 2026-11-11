<template>
  <!-- ok: sentinel-vue-link-javascript -->
  <a href="/inicio">Início</a>
</template>

<script setup>
// ok: sentinel-vue-options-api-mixins
import { useContador } from './useContador';
const { total } = useContador();
</script>
