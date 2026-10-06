import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { BrowsePage } from './browse';
import { DetailPage, omittedComponents } from './detail';
import { ConfirmationPage } from './purchase';
import { Pack } from '../../core/packs-api';
import { ProductPhoto } from '../../shared/product-photo';
import { HomePage } from './home';

const pack: Pack = {
  id: 'pack',
  code: 'test',
  name: 'Pack test',
  slogan: 'Exemple',
  guide: 'Guide test',
  status: 'PUBLISHED',
  demo: false,
  version: 1,
  componentNames: { fix: 'Fixations test', tool: 'Outil test' },
  variants: [
    {
      id: 'without',
      code: 'without',
      label: 'Sans outils',
      status: 'PUBLISHED',
      version: 0,
      price: { amount: '10.000', currency: 'TND' },
      available: 4,
      components: [{ variantId: 'fix', quantity: 2 }],
    },
    {
      id: 'complete',
      code: 'complete',
      label: 'Tout compris',
      status: 'PUBLISHED',
      version: 0,
      price: { amount: '14.375', currency: 'TND' },
      available: 2,
      components: [
        { variantId: 'fix', quantity: 3 },
        { variantId: 'tool', quantity: 1 },
      ],
    },
  ],
};
describe('Storefront routes and real API contracts', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  beforeEach(async () => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'catalogue', component: BrowsePage },
          { path: 'packs', component: BrowsePage, data: { kind: 'packs' } },
          { path: 'packs/:id', component: DetailPage, data: { kind: 'packs' } },
          { path: 'produits/:id', component: DetailPage },
          { path: 'confirmation/:id', component: ConfirmationPage },
        ]),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });
  afterEach(() => {
    http.verify();
    localStorage.clear();
  });
  it('passes search, filters, ordering and page to the server and ignores late previous results', async () => {
    const page = await harness.navigateByUrl('/catalogue?q=vis&sort=price_asc&page=2', BrowsePage);
    http.expectOne('/api/v1/categories').flush([]);
    http.expectOne('/api/v1/brands').flush([]);
    const previous = http.expectOne((r) => r.url === '/api/v1/products');
    expect(previous.request.params.get('q')).toBe('vis');
    expect(previous.request.params.get('page')).toBe('2');
    expect(previous.request.params.get('sort')).toBe('price_asc');
    await harness.navigateByUrl('/catalogue?q=outil');
    http
      .expectOne((r) => r.url === '/api/v1/products')
      .flush({ items: [], page: 0, size: 12, totalElements: 0 });
    previous.flush({ items: [], page: 2, size: 12, totalElements: 999 });
    expect(page.page()?.totalElements).toBe(0);
    harness.detectChanges();
    expect(harness.routeNativeElement?.textContent).toContain('Aucun produit trouvé');
  });
  it('shows a useful catalogue error and retries the actual request', async () => {
    const page = await harness.navigateByUrl('/catalogue', BrowsePage);
    http.expectOne('/api/v1/categories').flush([]);
    http.expectOne('/api/v1/brands').flush([]);
    http
      .expectOne((r) => r.url === '/api/v1/products')
      .flush({}, { status: 503, statusText: 'Unavailable' });
    harness.detectChanges();
    expect(harness.routeNativeElement?.querySelector('[role=alert]')?.textContent).toContain(
      'momentanément indisponible',
    );
    page.load();
    http
      .expectOne((r) => r.url === '/api/v1/products')
      .flush({ items: [], page: 0, size: 12, totalElements: 0 });
    expect(page.error()).toBe('');
  });
  it('explains pack contents, missing quantities and variant changes before adding to the cart', async () => {
    const page = await harness.navigateByUrl('/packs/pack', DetailPage);
    http.expectOne('/api/v1/packs/pack').flush(pack);
    http
      .expectOne('/api/v1/media/packs?ids=pack')
      .flush({
        pack: [
          {
            id: 'photo',
            packId: 'pack',
            cardUrl: '/kit-card.jpg',
            detailUrl: '/kit-detail.jpg',
            width: 1586,
            height: 992,
            sortOrder: 0,
            primary: true,
          },
        ],
      });
    harness.detectChanges();
    const image = harness.routeNativeElement!.querySelector('app-product-photo img')!;
    expect(image.getAttribute('srcset')).toBe('/kit-card.jpg 360w, /kit-detail.jpg 1200w');
    expect(image.getAttribute('loading')).toBe('lazy');
    expect(harness.routeNativeElement?.textContent).toContain('Fixations test');
    expect(harness.routeNativeElement?.textContent).toContain('Ce qui n’est pas inclus');
    expect(page.omitted()).toEqual([
      { variantId: 'fix', quantity: 1 },
      { variantId: 'tool', quantity: 1 },
    ]);
    await harness.fixture.whenStable();
    const choice = Array.from(harness.routeNativeElement!.querySelectorAll('label'))
      .find((label) => label.textContent?.includes('Tout compris'))!
      .querySelector('input')!;
    choice.click();
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(page.omitted()).toEqual([]);
    expect(harness.routeNativeElement?.textContent).toContain('14.375');
    page.quantity = 2;
    page.add();
    http.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    const request = http.expectOne('/api/v1/cart/estimate');
    expect(request.request.body.items).toEqual([
      { kind: 'PACK', offerId: 'complete', quantity: 2 },
    ]);
    request.flush({
      version: null,
      items: [],
      subtotalEstimate: { amount: '28.750', currency: 'TND' },
      shortages: [],
    });
  });
  it('shows zero-stock products as sold out and prevents adding them to the cart', async () => {
    const page = await harness.navigateByUrl('/produits/item', DetailPage);
    http.expectOne('/api/v1/products/item').flush({
      id: 'item', categoryId: 'category', brandId: null, name: 'Vis test', description: '',
      characteristics: {}, status: 'PUBLISHED', demo: false, version: 0,
      variants: [{ id: 'variant', sku: 'BC-TEST', label: 'Unité', unit: 'unité', options: {},
        price: { amount: '21.000', currency: 'TND' }, status: 'PUBLISHED', version: 0 }],
    });
    http.expectOne('/api/v1/availability/variant').flush({ variantId: 'variant', available: 0 });
    http.expectOne('/api/v1/media/products?ids=item').flush({});
    http.expectOne('/sqes-image-fallback.json').flush({});
    harness.detectChanges();
    const button = harness.routeNativeElement!.querySelector<HTMLButtonElement>('button[type=submit]')!;
    expect(button.disabled).toBe(true);
    expect(button.textContent).toContain('Épuisé');
    expect(harness.routeNativeElement?.textContent).toContain('ce produit n’est pas disponible');
    page.add();
    http.expectNone('/api/v1/cart/estimate');
  });
  it('reloads a confirmation using owner protected API and never fabricates an inaccessible receipt', async () => {
    const page = await harness.navigateByUrl('/confirmation/private', ConfirmationPage);
    http.expectOne('/api/v1/orders/private').flush({}, { status: 404, statusText: 'Not Found' });
    harness.detectChanges();
    expect(page.order()).toBeNull();
    expect(harness.routeNativeElement?.textContent).toContain('session actuelle');
  });
  it('uses actual pack photo metadata on cards and tolerates packs without photos', async () => {
    await harness.navigateByUrl('/packs', BrowsePage);
    http.expectOne('/api/v1/packs').flush([pack]);
    http
      .expectOne('/api/v1/media/packs?ids=pack')
      .flush({
        pack: [
          {
            id: 'photo',
            packId: 'pack',
            cardUrl: '/kit-card.jpg',
            detailUrl: '/kit-detail.jpg',
            width: 1586,
            height: 992,
            sortOrder: 0,
            primary: true,
          },
        ],
      });
    harness.detectChanges();
    expect(
      harness.routeNativeElement!.querySelector('app-offer-card img')!.getAttribute('src'),
    ).toBe('/kit-card.jpg');
    expect(harness.routeNativeElement!.querySelector('.pack-art')).toBeNull();
  });
  it('rejects fractional quantities with an accessible explanation and no cart request', async () => {
    await harness.navigateByUrl('/packs/pack', DetailPage);
    http.expectOne('/api/v1/packs/pack').flush(pack);
    http.expectOne('/api/v1/media/packs?ids=pack').flush({});
    harness.detectChanges();
    await harness.fixture.whenStable();
    const input: HTMLInputElement = harness.routeNativeElement!.querySelector('[name=quantity]')!;
    input.value = '1.5';
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(
      harness.routeNativeElement!.querySelector<HTMLButtonElement>('button[type=submit]')!.disabled,
    ).toBe(true);
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(harness.routeNativeElement!.querySelector('[role=alert]')!.textContent).toContain(
      'quantité entière',
    );
    http.expectNone('/api/v1/cart/estimate');
  });
  it('uses the designated primary image on the home page even when it is not first', () => {
    const fixture = TestBed.createComponent(HomePage);
    const photos = [
      { id: 'first', primary: false },
      { id: 'main', primary: true },
    ];
    fixture.componentInstance.images.set({ product: photos as never });
    expect(fixture.componentInstance.primaryImage('product')?.id).toBe('main');
  });
});
describe('Pack comparison and responsive media', () => {
  it('compares actual component quantities instead of inferring content from variant names', () => {
    expect(omittedComponents(pack, pack.variants[0])).toEqual([
      { variantId: 'fix', quantity: 1 },
      { variantId: 'tool', quantity: 1 },
    ]);
    expect(omittedComponents(pack, pack.variants[1])).toEqual([]);
  });
  it('uses derived responsive images, lazy loading and a recoverable accessible fallback', () => {
    TestBed.configureTestingModule({ imports: [ProductPhoto] });
    const fixture = TestBed.createComponent(ProductPhoto);
    fixture.componentRef.setInput('image', {
      id: 'photo',
      productId: 'product',
      cardUrl: '/card.jpg',
      detailUrl: '/detail.jpg',
      width: 1800,
      height: 1200,
      primary: true,
      sortOrder: 0,
    });
    fixture.componentRef.setInput('alt', 'Article test');
    fixture.detectChanges();
    const image: HTMLImageElement = fixture.nativeElement.querySelector('img');
    expect(image.getAttribute('loading')).toBe('lazy');
    expect(image.getAttribute('srcset')).toBe('/card.jpg 360w, /detail.jpg 1200w');
    expect(image.alt).toBe('Article test');
    image.dispatchEvent(new Event('error'));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Photo indisponible');
  });
});
