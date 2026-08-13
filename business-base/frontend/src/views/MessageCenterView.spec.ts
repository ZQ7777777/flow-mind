import { flushPromises, mount } from "@vue/test-utils";
import { createPinia } from "pinia";
import { afterEach, describe, expect, it, vi } from "vitest";
import MessageCenterView from "./MessageCenterView.vue";

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

function mockPage(records: unknown[], unreadCount = 0): ReturnType<typeof vi.fn> {
  return vi.fn().mockResolvedValue(
    jsonResponse({
      records,
      pageNo: 1,
      pageSize: 20,
      unreadCount,
    }),
  );
}

function mountView() {
  return mount(MessageCenterView, {
    global: {
      plugins: [createPinia()],
      stubs: {
        RouterLink: {
          props: ["to"],
          template: '<a :href="to"><slot /></a>',
        },
      },
    },
  });
}

describe("MessageCenterView", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders messages with the unread count and a mark-read action", async () => {
    vi.stubGlobal(
      "fetch",
      mockPage(
        [
          {
            messageId: "m1",
            messageType: "ALERT",
            title: "告警A",
            content: "内容",
            readStatus: "UNREAD",
            severity: "HIGH",
          },
        ],
        1,
      ),
    );

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.find('[data-test="message-row-m1"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="unread-count"]').text()).toContain("1 条未读");
    expect(wrapper.find('[data-test="message-mark-read-m1"]').exists()).toBe(true);
  });

  it("marks a message read and hides the action button", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse({
          records: [
            {
              messageId: "m1",
              messageType: "TASK_REMIND",
              title: "t",
              readStatus: "UNREAD",
            },
          ],
          pageNo: 1,
          pageSize: 20,
          unreadCount: 1,
        }),
      )
      .mockResolvedValue(jsonResponse({}));
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mountView();
    await flushPromises();

    await wrapper.find('[data-test="message-mark-read-m1"]').trigger("click");
    await flushPromises();

    expect(fetchMock.mock.calls[1][0]).toContain("/api/messages/m1/read");
    expect(wrapper.find('[data-test="message-mark-read-m1"]').exists()).toBe(false);
  });

  it("marks all messages read via the toolbar button", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse({
          records: [
            {
              messageId: "m1",
              messageType: "ALERT",
              title: "t",
              readStatus: "UNREAD",
            },
          ],
          pageNo: 1,
          pageSize: 20,
          unreadCount: 1,
        }),
      )
      .mockResolvedValue(jsonResponse({}));
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mountView();
    await flushPromises();

    await wrapper.find('[data-test="messages-mark-all-read"]').trigger("click");
    await flushPromises();

    expect(fetchMock.mock.calls[1][0]).toContain("/api/messages/read-all");
    expect(wrapper.find('[data-test="unread-count"]').text()).toContain("0 条未读");
    expect(wrapper.find('[data-test="message-mark-read-m1"]').exists()).toBe(false);
  });

  it("applies read-status and type filters via the query button", async () => {
    const fetchMock = mockPage([]);
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mountView();
    await flushPromises();

    await wrapper.find('[data-test="messages-filter-readStatus"]').setValue("UNREAD");
    await wrapper.find('[data-test="messages-filter-type"]').setValue("ALERT");
    await wrapper.find("form").trigger("submit");
    await flushPromises();

    const lastCall = fetchMock.mock.calls[fetchMock.mock.calls.length - 1][0] as string;
    expect(lastCall).toContain("readStatus=UNREAD");
    expect(lastCall).toContain("messageType=ALERT");
  });
});
