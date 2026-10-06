import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CatalogApi, ProductImage } from './catalog-api';

describe('SQES image fallback', () => {
  let api: CatalogApi;
  let http: HttpTestingController;
  const id = 'ab123456-0000-0000-0000-000000000001';

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(CatalogApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('keeps native photos first and appends missing source gallery photos', () => {
    let images: ProductImage[] = [];
    api.publicImages([id], true).subscribe(result => images = result[id]);
    const native: ProductImage = { id: 'native', productId: id,
      cardUrl: '/api/v1/media/native/card', detailUrl: '/api/v1/media/native/detail',
      width: 640, height: 480, sortOrder: 0, primary: true };
    http.expectOne(`/api/v1/media/products?ids=${id}`).flush({ [id]: [native] });
    http.expectOne('/sqes-galleries/ab.json').flush({
      [id]: [
        ['source-1', 'https://cdn.shopify.com/files/one.jpg', 640, 480],
        ['source-2', 'https://cdn.shopify.com/files/two.jpg', 800, 600],
      ],
    });
    expect(images.map(image => image.id)).toEqual(['native', 'sqes-source-2']);
    expect(images[1].detailUrl).toContain('width=1200');
    expect(images[1].primary).toBe(false);
  });

  it('shows the source gallery if there is no native image and ignores foreign hosts', () => {
    let images: ProductImage[] = [];
    api.publicImages([id], true).subscribe(result => images = result[id]);
    http.expectOne(`/api/v1/media/products?ids=${id}`).flush({});
    http.expectOne('/sqes-galleries/ab.json').flush({
      [id]: [
        ['source-1', 'https://cdn.shopify.com/files/one.jpg', 640, 480],
        ['source-2', 'https://unexpected.example/two.jpg', 800, 600],
      ],
    });
    expect(images.length).toBe(1);
    expect(images[0].primary).toBe(true);
    expect(images[0].cardUrl).toContain('width=360');
  });
});
