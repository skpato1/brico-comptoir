import { Component, input } from '@angular/core';

@Component({
  selector: 'app-department-icon',
  template: `<svg viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="2.2"
    stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
    @switch (kind()) {
      @case ('tools') { <path d="m10 38 18-18m-6-7 13 13M27 8l13 13M8 31l9 9m-9 0 9-9"/> }
      @case ('water') { <path d="M24 5c-6 9-14 18-14 26a14 14 0 0 0 28 0C38 23 30 14 24 5Z"/><path d="M17 31a7 7 0 0 0 7 7"/> }
      @case ('light') { <path d="M16 29a14 14 0 1 1 16 0l-2 5H18l-2-5Zm3 9h10m-9 4h8"/><path d="M24 13v9m-4-4 4 4 4-4"/> }
      @case ('paint') { <path d="M8 12h25v14H8zM33 15h5v8h-5m-15 3v5h6m0 0v10"/><path d="M11 17h18"/> }
      @case ('leaf') { <path d="M39 8C19 8 9 17 9 30a10 10 0 0 0 10 10C32 40 39 28 39 8Z"/><path d="M9 39c8-11 15-17 25-24"/> }
      @case ('fixing') { <path d="M24 5v38M12 15h24M12 33h24M16 10v10m16-10v10M16 28v10m16-10v10"/> }
      @case ('home') { <path d="m6 22 18-15 18 15v19H6V22Zm14 19V27h8v14"/> }
      @case ('building') { <path d="M8 42V16L25 8v34M25 20h15v22M13 22h6m-6 8h6m12-3h5m-5 8h5M5 42h38"/> }
      @default { <rect x="7" y="7" width="14" height="14" rx="2"/><rect x="27" y="7" width="14" height="14" rx="2"/><rect x="7" y="27" width="14" height="14" rx="2"/><rect x="27" y="27" width="14" height="14" rx="2"/> }
    }
  </svg>`,
  styles: [':host { display: inline-grid; place-items: center; flex: none } svg { width: 100%; height: 100% }'],
})
export class DepartmentIcon {
  readonly kind = input('grid');
}
