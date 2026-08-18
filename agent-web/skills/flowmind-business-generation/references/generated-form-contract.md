# Generated standalone entry page contract

Generate exactly these files for one business:

```text
business-base/frontend/src/modules/generated/{business-code}/BusinessForm.vue
business-base/frontend/src/modules/generated/{business-code}/Apply.vue
business-base/frontend/src/modules/generated/{business-code}/__tests__/BusinessForm.spec.ts
business-base/frontend/src/modules/generated/{business-code}/__tests__/Apply.spec.ts
business-base/frontend/src/router/generated-routes.ts
```

## BusinessForm.vue

Accept these props:

- `modelValue: Record<string, unknown>`
- `fields: WorkflowFormField[]`
- `fieldPermissions: WorkflowFieldPermission[]`
- `mode: "edit" | "readonly"`
- `disabled: boolean`

Emit `update:modelValue` with a new object. Expose `validate(): Promise<boolean>`.

Render only fields present in the confirmed requirement and match them by exact `fieldCode`. Hide fields whose runtime permission has `visible: false`. Disable fields unless both the mode and permission allow editing. Combine the definition-level `required` flag with the current node permission when building validation rules.

Normalize values before emitting updates. DOM input values are strings even for `<input type="number">`; for fields whose `fieldType` or `controlType` is `number`, emit a JavaScript `number` for every non-empty valid input value and keep empty values empty for required validation. This prevents backend errors such as `字段类型不匹配: amount`, because workflow start-submit validates number fields as JSON numbers.

Do not render submit/reset controls. Do not import workflow APIs, attachments, the router, stores, or task action components.

`BusinessForm.spec.ts` must include coverage that entering a number field emits a numeric value, for example `1000.25` with `typeof value === "number"`, not the string `"1000.25"`.

## Apply.vue

Render a complete standalone business entry page. The page should be visually self-contained and suitable for direct URL access, with a generated page container, business title, optional short intro/status area, and scoped responsive styling.

Inside that standalone page, render the shared start shell and provide the fixed `processCode` from the confirmed requirement and the generated `BusinessForm` component. The shared shell remains responsible for loading start context, rendering start attachments, submitting the process, and showing runtime success or error states.

Do not duplicate context loading, submission, attachments, success/error state, or navigation.

## generated-routes.ts

Export both the generated route array and a `processCode` form-component registry. Preserve every unrelated existing route and registry entry. Use lazy imports.

The generated apply route must render outside the `business-base` application shell. In the current target application, set `meta.public: true` on the generated apply route because `App.vue` renders public routes directly through the top-level `RouterView`.

Use this route shape:

```ts
{
  path: "/generated/{business-code}/apply",
  name: "generated-{business-code}-apply",
  meta: { public: true, title: "{entry-display-name}" },
  component: () => import("../modules/generated/{business-code}/Apply.vue"),
}
```
