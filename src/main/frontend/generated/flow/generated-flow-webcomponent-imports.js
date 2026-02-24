import { injectGlobalWebcomponentCss } from 'Frontend/generated/jar-resources/theme-util.js';

import '@vaadin/vertical-layout/src/vaadin-vertical-layout.js';
import '@vaadin/app-layout/src/vaadin-app-layout.js';
import '@vaadin/multi-select-combo-box/src/vaadin-multi-select-combo-box.js';
import 'Frontend/generated/jar-resources/flow-component-renderer.js';
import 'Frontend/generated/jar-resources/flow-component-directive.js';
import 'Frontend/generated/jar-resources/comboBoxConnector.js';
import '@vaadin/tooltip/src/vaadin-tooltip.js';
import '@vaadin/horizontal-layout/src/vaadin-horizontal-layout.js';
import '@vaadin/dialog/src/vaadin-dialog.js';
import '@vaadin/button/src/vaadin-button.js';
import 'Frontend/generated/jar-resources/disableOnClickFunctions.js';
import '@vaadin/notification/src/vaadin-notification.js';
import '@vaadin/icons/vaadin-iconset.js';
import '@vaadin/icon/src/vaadin-icon.js';
import '@vaadin/app-layout/src/vaadin-drawer-toggle.js';
import '@vaadin/text-field/src/vaadin-text-field.js';
import '@vaadin/checkbox-group/src/vaadin-checkbox-group.js';
import 'Frontend/generated/jar-resources/lit-renderer.ts';
import '@vaadin/checkbox/src/vaadin-checkbox.js';
import '@vaadin/side-nav/src/vaadin-side-nav.js';
import '@vaadin/side-nav/src/vaadin-side-nav-item.js';
import '@vaadin/markdown/src/vaadin-markdown.js';
import '@vaadin/grid/src/vaadin-grid.js';
import '@vaadin/grid/src/vaadin-grid-column.js';
import '@vaadin/grid/src/vaadin-grid-sorter.js';
import 'Frontend/generated/jar-resources/gridConnector.ts';
import 'Frontend/generated/jar-resources/vaadin-grid-flow-selection-column.js';
import '@vaadin/grid/src/vaadin-grid-column-group.js';
import '@vaadin/context-menu/src/vaadin-context-menu.js';
import 'Frontend/generated/jar-resources/contextMenuConnector.js';
import 'Frontend/generated/jar-resources/contextMenuTargetConnector.js';
import '@vaadin/split-layout/src/vaadin-split-layout.js';
import '@vaadin/text-area/src/vaadin-text-area.js';
import '@vaadin/progress-bar/src/vaadin-progress-bar.js';
import '@vaadin/radio-group/src/vaadin-radio-group.js';
import '@vaadin/radio-group/src/vaadin-radio-button.js';
import '@vaadin/combo-box/src/vaadin-combo-box.js';
import '@vaadin/grid/src/vaadin-grid-tree-toggle.js';
import 'Frontend/generated/jar-resources/treeGridConnector.ts';
import '@vaadin/form-layout/src/vaadin-form-layout.js';
import '@vaadin/form-layout/src/vaadin-form-item.js';
import '@vaadin/form-layout/src/vaadin-form-row.js';
import '@vaadin/tabs/src/vaadin-tabs.js';
import '@vaadin/tabs/src/vaadin-tab.js';
import 'Frontend/generated/jar-resources/menubarConnector.js';
import '@vaadin/menu-bar/src/vaadin-menu-bar.js';
import '@vaadin/confirm-dialog/src/vaadin-confirm-dialog.js';
import '@vaadin/password-field/src/vaadin-password-field.js';
import '@vaadin/upload/src/vaadin-upload.js';
import '@vaadin/select/src/vaadin-select.js';
import 'Frontend/generated/jar-resources/selectConnector.js';
import '@vaadin/integer-field/src/vaadin-integer-field.js';
import '@vaadin/tabsheet/src/vaadin-tabsheet.js';
import '@vaadin/common-frontend/ConnectionIndicator.js';
import 'Frontend/generated/jar-resources/ReactRouterOutletElement.tsx';

const loadOnDemand = (key) => {
  const pending = [];
  if (key === '8528a9237e5487e5352704c0dd9483e21747cac8b6791e3ebb5af9675fee3827') {
    pending.push(import('./chunks/chunk-962a3421c4d1891734e353cc79a993d4b866281073c182ceecd4e5685a7964e3.js'));
  }
  if (key === 'd42209aa866d3d5ed0fd7209540c35d0a2838ebfbd657edaeddac7633d2463a3') {
    pending.push(import('./chunks/chunk-962a3421c4d1891734e353cc79a993d4b866281073c182ceecd4e5685a7964e3.js'));
  }
  if (key === '722f0e2e42d367ab0d7edc393bba7be86dde2c26f229b51f983c463aca6868ad') {
    pending.push(import('./chunks/chunk-615c47fa8d04ea878dc688296ba1b6fbe547d880044b643d2f9e260a843dd93a.js'));
  }
  if (key === 'cc046a7961f2ad1ecd388e8e8a96541491ce9ee8324c2baae5d52247ce662a9a') {
    pending.push(import('./chunks/chunk-962a3421c4d1891734e353cc79a993d4b866281073c182ceecd4e5685a7964e3.js'));
  }
  if (key === 'a0f764f3beb7843f6d60ddaa3d5aea37f08ad85133e0e9ac8d88a8567fb05a9c') {
    pending.push(import('./chunks/chunk-962a3421c4d1891734e353cc79a993d4b866281073c182ceecd4e5685a7964e3.js'));
  }
  if (key === 'a24db939f64459f2aae790503ec78ea4f831335f0e0fa9c3f6803723df91051a') {
    pending.push(import('./chunks/chunk-962a3421c4d1891734e353cc79a993d4b866281073c182ceecd4e5685a7964e3.js'));
  }
  if (key === '6a5d593bf87daace3c47e7ab2ea41369db32570710da210909bd1faa813e2167') {
    pending.push(import('./chunks/chunk-962a3421c4d1891734e353cc79a993d4b866281073c182ceecd4e5685a7964e3.js'));
  }
  if (key === 'a98125c4ccbac2b1249bcb446f5a6c7926d29f964ab2b829367e9bce8aaf032a') {
    pending.push(import('./chunks/chunk-962a3421c4d1891734e353cc79a993d4b866281073c182ceecd4e5685a7964e3.js'));
  }
  if (key === 'abb4ae4df658a2dca76409e772d0b16da14d6354c029ccc21bf3c3eb1f2bac1e') {
    pending.push(import('./chunks/chunk-da72f3bd06c7cb5cf9cd2493d0b859f2175e86122097410097f3882e58c1f59c.js'));
  }
  return Promise.all(pending);
}
window.Vaadin = window.Vaadin || {};
window.Vaadin.Flow = window.Vaadin.Flow || {};
window.Vaadin.Flow.loadOnDemand = loadOnDemand;
window.Vaadin.Flow.resetFocus = () => {
 let ae=document.activeElement;
 while(ae&&ae.shadowRoot) ae = ae.shadowRoot.activeElement;
 return !ae || ae.blur() || ae.focus() || true;
}