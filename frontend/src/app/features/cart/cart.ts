import { Component, Input, OnChanges } from '@angular/core';
import { Account } from '../../core/identity-api';
import { CartItem } from '../../core/cart-api';
import { CartState } from '../../core/cart-state';

@Component({
  selector: 'app-cart',
  templateUrl: './cart.html',
  styleUrl: './cart.scss',
})
export class CartComponent implements OnChanges {
  @Input() account: Account | null = null;
  @Input() manageSession = true;
  constructor(readonly cart: CartState) { }
  ngOnChanges(): void { if (this.manageSession) this.cart.activate(this.account); }
  quantityChanged(event: Event, item: CartItem): void {
    const raw = (event.target as HTMLInputElement).value;
    const value = Number(raw);
    this.cart.change(item.kind, item.offerId, raw.trim() && value >= 1 ? value : Number.NaN);
  }
}
