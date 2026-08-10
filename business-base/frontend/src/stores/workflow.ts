import { defineStore } from "pinia";
import {
  fetchInstanceDetail,
  fetchTaskDetail,
  fetchWorkflowList,
  performTaskAction,
} from "../api/workflow";
import type {
  PageRequest,
  PageResponse,
  TaskActionCode,
  TaskActionPayload,
  TaskActionResult,
  WorkflowDetailResponse,
  WorkflowListItem,
  WorkflowListType,
} from "../types/workflow";

interface ListState {
  type: WorkflowListType;
  params: PageRequest;
  items: WorkflowListItem[];
  total: number;
  loading: boolean;
  error: string;
}

interface DetailState {
  data: WorkflowDetailResponse | null;
  loading: boolean;
  error: string;
}

interface WorkflowState {
  list: ListState;
  detail: DetailState;
  actionSubmitting: boolean;
}

const defaultPage: PageRequest = { pageNo: 1, pageSize: 20 };

export const useWorkflowStore = defineStore("workflow", {
  state: (): WorkflowState => ({
    list: {
      type: "todo",
      params: { ...defaultPage },
      items: [],
      total: 0,
      loading: false,
      error: "",
    },
    detail: {
      data: null,
      loading: false,
      error: "",
    },
    actionSubmitting: false,
  }),
  actions: {
    async loadList(type: WorkflowListType, params: PageRequest): Promise<void> {
      const requestId = nextRequestId();
      this.list.type = type;
      this.list.params = { ...params };
      this.list.loading = true;
      this.list.error = "";
      latestListRequestId = requestId;

      try {
        const page: PageResponse<WorkflowListItem> = await fetchWorkflowList(type, params);
        if (latestListRequestId !== requestId) {
          return;
        }
        this.list.items = page.items;
        this.list.total = page.total;
        this.list.params = { pageNo: page.pageNo, pageSize: page.pageSize };
      } catch (error) {
        if (latestListRequestId === requestId) {
          this.list.error = error instanceof Error ? error.message : "列表加载失败";
        }
      } finally {
        if (latestListRequestId === requestId) {
          this.list.loading = false;
        }
      }
    },

    async loadTaskDetail(taskId: string): Promise<void> {
      await this.loadDetail(() => fetchTaskDetail(taskId));
    },

    async loadInstanceDetail(instanceId: string): Promise<void> {
      await this.loadDetail(() => fetchInstanceDetail(instanceId));
    },

    async submitAction(
      taskId: string,
      action: TaskActionCode,
      payload: TaskActionPayload,
    ): Promise<TaskActionResult> {
      this.actionSubmitting = true;
      try {
        return await performTaskAction(taskId, action, payload);
      } finally {
        this.actionSubmitting = false;
      }
    },

    async loadDetail(loader: () => Promise<WorkflowDetailResponse>): Promise<void> {
      this.detail.loading = true;
      this.detail.error = "";
      try {
        this.detail.data = await loader();
      } catch (error) {
        this.detail.error = error instanceof Error ? error.message : "详情加载失败";
      } finally {
        this.detail.loading = false;
      }
    },
  },
});

let latestListRequestId = 0;

function nextRequestId(): number {
  latestListRequestId += 1;
  return latestListRequestId;
}
