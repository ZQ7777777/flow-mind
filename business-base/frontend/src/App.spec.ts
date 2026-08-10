import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import App from "./App.vue";

describe("App navigation", () => {
  it("shows generated entry routes first and derives a business label from route name", () => {
    const wrapper = mount(App, {
      global: {
        stubs: {
          RouterLink: {
            props: ["to"],
            template: '<a class="nav-link" :href="to"><slot /></a>',
          },
          RouterView: true,
        },
      },
    });

    expect(wrapper.findAll(".nav-link").map((link) => link.text())).toEqual([
      "入金申请",
      "我发起的",
      "我的待办",
      "我的已办",
    ]);
  });
});
