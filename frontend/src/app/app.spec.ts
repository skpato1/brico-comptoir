import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { App } from './app';

describe('API availability page', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(App);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('keeps the check button disabled until the actual API responds', async () => {
    expect(fixture.nativeElement.querySelector('button').disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Vérification en cours');
    const request = http.expectOne('/api/v1/health');
    expect(request.request.method).toBe('GET');
    request.flush({ status: 'UP' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('API disponible');
    expect(fixture.nativeElement.querySelector('button').disabled).toBe(false);
  });

  it('shows an outage and can recover after a real retry', async () => {
    http.expectOne('/api/v1/health').flush({ status: 'DOWN' }, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('API indisponible');
    fixture.nativeElement.querySelector('button').click();
    http.expectOne('/api/v1/health').flush({ status: 'UP' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('API disponible');
  });

  it('does not report success for an unexpected response body', async () => {
    http.expectOne('/api/v1/health').flush({ message: 'not a health response' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('API indisponible');
  });

  it('handles a network error without leaving the page loading', async () => {
    http.expectOne('/api/v1/health').error(new ProgressEvent('error'));
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('API indisponible');
    expect(fixture.nativeElement.querySelector('button').disabled).toBe(false);
  });
});
