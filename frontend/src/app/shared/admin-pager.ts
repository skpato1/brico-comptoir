import { Component, Input, Output, EventEmitter } from '@angular/core';
@Component({
  selector: 'app-admin-pager',
  template: `<nav class="actions" aria-label="Pagination">
    <button
      type="button"
      class="secondary"
      (click)="changed.emit(page - 1)"
      [disabled]="busy || page === 0"
    >
      Précédent
    </button>
    <span>Page {{ page + 1 }} · {{ total }} résultat(s)</span>
    <button
      type="button"
      class="secondary"
      (click)="changed.emit(page + 1)"
      [disabled]="busy || (page + 1) * size >= total"
    >
      Suivant
    </button>
  </nav>`,
})
export class AdminPager {
  @Input() page = 0;
  @Input() size = 20;
  @Input() total = 0;
  @Input() busy = false;
  @Output() changed = new EventEmitter<number>();
}
