import { defineStore } from "pinia";
import { fetchAlerts, handleAlert } from "../api/alert";
import type {
  AlertHandlePayload,
  AlertListQuery,
  AlertRecord,
} from "../types/alert";

interface AlertFilters {
  alertStatus: string;
  alertType: string;
  severity: string;
  instanceId: string;
}

interface AlertState {
  records: AlertRecord[];
  pageNo: number;
  pageSize: number;
  total: number;
  totalPages: number;
  loading: boolean;
  error: string;
  handleLoading: boolean;
  handleError: string;
  filters: AlertFilters;
}

export const useAlertStore = defineStore("alert", {
  state: (): AlertState => ({
    records: [],
    pageNo: 1,
    pageSize: 20,
    total: 0,
    totalPages: 0,
    loading: false,
    error: "",
    handleLoading: false,
    handleError: "",
    filters: {
      alertStatus: "",
      alertType: "",
      severity: "",
      instanceId: "",
    },
  }),
  actions: {
    async loadAlerts(pageNo?: number): Promise<void> {
      const targetPage = pageNo ?? this.pageNo;
      this.pageNo = targetPage;
      this.loading = true;
      this.error = "";
      const query: AlertListQuery = {
        pageNo: targetPage,
        pageSize: this.pageSize,
        alertStatus: this.filters.alertStatus || undefined,
        alertType: this.filters.alertType || undefined,
        severity: this.filters.severity || undefined,
        instanceId: this.filters.instanceId.trim() || undefined,
      };
      try {
        const page = await fetchAlerts(query);
        this.records = page.records;
        this.pageNo = page.pageNo;
        this.pageSize = page.pageSize;
        this.total = page.total;
        this.totalPages = page.totalPages;
      } catch (error) {
        this.error = error instanceof Error ? error.message : "告警加载失败";
      } finally {
        this.loading = false;
      }
    },

    applyFilters(filters: Partial<AlertFilters>): void {
      this.filters = { ...this.filters, ...filters };
      this.pageNo = 1;
      void this.loadAlerts(1);
    },

    /** 处理或忽略告警，成功后用返回的告警记录更新列表。 */
    async handleAlert(
      alertId: string,
      payload: AlertHandlePayload,
      idempotencyKey: string,
    ): Promise<AlertRecord> {
      this.handleLoading = true;
      this.handleError = "";
      try {
        const updated = await handleAlert(alertId, payload, idempotencyKey);
        const index = this.records.findIndex((item) => item.alertId === alertId);
        if (index >= 0) {
          this.records[index] = updated;
        }
        return updated;
      } catch (error) {
        this.handleError = error instanceof Error ? error.message : "告警处理失败";
        throw error;
      } finally {
        this.handleLoading = false;
      }
    },
  },
});
