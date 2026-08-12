import { defineStore } from "pinia";
import { WorkflowApiError } from "../api/http";
import {
  fetchInstanceDetail,
  fetchTaskDetail,
  fetchWorkflowList,
  performTaskAction,
} from "../api/workflow";
import type {
  WorkflowListQuery,
  WorkflowPageResponse,
  TaskActionCode,
  TaskActionPayload,
  WorkflowTaskActionResponse,
  WorkflowDetailResponse,
  WorkflowListRecord,
  WorkflowListType,
} from "../types/workflow";

interface ListState {
  type: WorkflowListType;
  params: WorkflowListQuery;
  records: WorkflowListRecord[];
  total: number;
  totalPages: number;
  loading: boolean;
  error: string;
}

interface DetailState {
  data: WorkflowDetailResponse | null;
  loading: boolean;
  error: string;
  errorCode: string;
}

interface WorkflowState {
  list: ListState;
  detail: DetailState;
  actionSubmitting: boolean;
  actionError: string;
}

const defaultPage: WorkflowListQuery = { pageNo: 1, pageSize: 20 };

export const useWorkflowStore = defineStore("workflow", {
  state: (): WorkflowState => ({
    list: {
      type: "todo",
      params: { ...defaultPage },
      records: [],
      total: 0,
      totalPages: 0,
      loading: false,
      error: "",
    },
    detail: {
      data: null,
      loading: false,
      error: "",
      errorCode: "",
    },
    actionSubmitting: false,
    actionError: "",
  }),
  actions: {
    async loadList(type: WorkflowListType, params: WorkflowListQuery): Promise<void> {
      const requestId = nextRequestId();
      this.list.type = type;
      this.list.params = { ...params };
      this.list.loading = true;
      this.list.error = "";
      latestListRequestId = requestId;

      try {
        const page: WorkflowPageResponse<WorkflowListRecord> = await fetchWorkflowList(type, params);
        if (latestListRequestId !== requestId) {
          return;
        }
        this.list.records = page.records;
        this.list.total = page.total;
        this.list.totalPages = page.totalPages;
        this.list.params = { ...params, pageNo: page.pageNo, pageSize: page.pageSize };
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
    ): Promise<WorkflowTaskActionResponse> {
      this.actionSubmitting = true;
      this.actionError = "";
      try {
        return await performTaskAction(taskId, action, payload);
      } catch (error) {
        this.actionError = error instanceof Error ? error.message : "任务办理失败";
        throw error;
      } finally {
        this.actionSubmitting = false;
      }
    },

    async loadDetail(loader: () => Promise<WorkflowDetailResponse>): Promise<void> {
      this.detail.loading = true;
      this.detail.error = "";
      this.detail.errorCode = "";
      try {
        this.detail.data = await loader();
      } catch (error) {
        this.detail.error = error instanceof Error ? error.message : "详情加载失败";
        this.detail.errorCode = error instanceof WorkflowApiError ? error.code : "";
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
