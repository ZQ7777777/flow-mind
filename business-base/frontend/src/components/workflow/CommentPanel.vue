<script setup lang="ts">
import type { WorkflowComment } from "../../types/workflow";
import { formatDateTime } from "../../utils/format";

defineProps<{
  comments: WorkflowComment[];
}>();
</script>

<template>
  <section class="workflow-section" aria-labelledby="comments-heading">
    <div class="section-header">
      <h2 id="comments-heading">审批意见</h2>
    </div>
    <ul v-if="comments.length" class="comment-list">
      <li v-for="(comment, index) in comments" :key="comment.commentId ?? index">
        <div>
          <strong>{{ comment.operatorName ?? "--" }}</strong>
          <span>{{ comment.action ?? "--" }}</span>
          <span>{{ formatDateTime(comment.completedAt) }}</span>
        </div>
        <p>{{ comment.content || "--" }}</p>
      </li>
    </ul>
    <p v-else class="empty-state">暂无审批意见</p>
  </section>
</template>

<style scoped>
.workflow-section {
  padding: 20px 0;
  border-top: 1px solid #e5e7eb;
}

.section-header {
  margin-bottom: 12px;
}

h2 {
  margin: 0;
  color: #111827;
  font-size: 18px;
  font-weight: 650;
}

.comment-list {
  display: grid;
  gap: 10px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.comment-list li {
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
}

.comment-list div {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: #6b7280;
  font-size: 13px;
}

.comment-list strong {
  color: #111827;
}

.comment-list p {
  margin: 8px 0 0;
  overflow-wrap: anywhere;
  color: #374151;
}

.empty-state {
  margin: 0;
  color: #6b7280;
}
</style>
