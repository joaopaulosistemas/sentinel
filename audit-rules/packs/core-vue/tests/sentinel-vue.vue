<template>
  <div>
    <!-- ruleid: sentinel-vue-v-html -->
    <div v-html="conteudo"></div>
    <!-- ok: sentinel-vue-v-html -->
    <div>{{ conteudo }}</div>

    <!-- ruleid: sentinel-vue-v-for-sem-key -->
    <li v-for="item in itens">{{ item.nome }}</li>
    <!-- ok: sentinel-vue-v-for-sem-key -->
    <li v-for="item in itens" :key="item.id">{{ item.nome }}</li>
    <!-- ok: sentinel-vue-v-for-sem-key -->
    <template v-for="item in itens"><span :key="item.id">{{ item.nome }}</span></template>

    <!-- ruleid: sentinel-vue-v-if-com-v-for -->
    <li v-for="item in itens" v-if="item.ativo" :key="item.id">{{ item.nome }}</li>
    <!-- ok: sentinel-vue-v-if-com-v-for -->
    <li v-for="item in ativos" :key="item.id">{{ item.nome }}</li>

    <!-- ruleid: sentinel-vue-link-javascript -->
    <a href="javascript:apagar()">apagar</a>

    <!-- ruleid: sentinel-vue-target-blank-sem-rel -->
    <a href="https://exemplo.com" target="_blank">site</a>
    <!-- ok: sentinel-vue-target-blank-sem-rel -->
    <a href="https://exemplo.com" target="_blank" rel="noopener noreferrer">site</a>
  </div>
</template>

<script setup lang="ts">
import { reactive, toRefs } from 'vue';

const estado = reactive({ a: 1, b: 2 });
// ruleid: sentinel-vue-reactive-desestruturado
const { a, b } = reactive({ a: 1, b: 2 });
// ok: sentinel-vue-reactive-desestruturado
const { a: x } = toRefs(estado);

// ruleid: sentinel-vue-define-props-sem-tipo
const props = defineProps(['titulo']);
// ok: sentinel-vue-define-props-sem-tipo
const props2 = defineProps<{ titulo: string }>();
</script>

<script lang="ts">
export default {
  // ruleid: sentinel-vue-options-api-mixins
  mixins: [ajuda],
};
</script>
