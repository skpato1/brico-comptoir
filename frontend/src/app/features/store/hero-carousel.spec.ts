import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HeroCarousel } from './hero-carousel';
import { vi } from 'vitest';

describe('HeroCarousel accessibility and rotation', () => {
  let reduced = false;
  let motionChanged: () => void;
  const removeListener = vi.fn();
  beforeEach(() => {
    reduced = false;
    vi.useFakeTimers();
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => ({
        get matches() {
          return reduced;
        },
        addEventListener: (_: string, callback: () => void) => {
          motionChanged = callback;
        },
        removeEventListener: removeListener,
      })),
    );
    TestBed.configureTestingModule({ imports: [HeroCarousel], providers: [provideRouter([])] });
  });
  afterEach(() => {
    TestBed.resetTestingModule();
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });
  function create() {
    const fixture = TestBed.createComponent(HeroCarousel);
    fixture.detectChanges();
    fixture.componentInstance.hidden.set(false);
    return fixture;
  }
  it('rotates slowly, pauses on hover and explicit pause, and cleans up', () => {
    const f = create();
    const c = f.componentInstance;
    vi.advanceTimersByTime(6500);
    expect(c.active()).toBe(1);
    c.hovered.set(true);
    vi.advanceTimersByTime(6500);
    expect(c.active()).toBe(1);
    c.hovered.set(false);
    c.toggleRotation();
    vi.advanceTimersByTime(6500);
    expect(c.active()).toBe(1);
    c.toggleRotation();
    vi.advanceTimersByTime(6500);
    expect(c.active()).toBe(2);
    f.destroy();
    vi.advanceTimersByTime(6500);
    expect(c.active()).toBe(2);
    expect(removeListener).toHaveBeenCalled();
  });
  it('wraps manual navigation, hides inactive links and stops on keyboard focus', () => {
    const f = create();
    const c = f.componentInstance;
    c.select(-1);
    f.detectChanges();
    expect(c.active()).toBe(2);
    const slides = f.nativeElement.querySelectorAll('.slide');
    expect(slides[0].hasAttribute('inert')).toBe(true);
    expect(slides[2].hasAttribute('inert')).toBe(false);
    c.toggleRotation();
    f.nativeElement
      .querySelector('.caption a')
      .dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
    expect(c.paused()).toBe(true);
    f.nativeElement
      .querySelector('.carousel')
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    expect(c.active()).toBe(0);
  });
  it('never auto-rotates with reduced motion, including preference changes', () => {
    reduced = true;
    const f = create();
    const c = f.componentInstance;
    vi.advanceTimersByTime(13000);
    expect(c.active()).toBe(0);
    expect(f.nativeElement.querySelector('[data-rotation]').disabled).toBe(true);
    c.select(1);
    expect(c.active()).toBe(1);
    reduced = false;
    motionChanged();
    c.toggleRotation();
    vi.advanceTimersByTime(6500);
    expect(c.active()).toBe(2);
    reduced = true;
    motionChanged();
    vi.advanceTimersByTime(6500);
    expect(c.active()).toBe(2);
  });
  it('pauses in hidden tabs and distinguishes horizontal swipes from scrolling', () => {
    const f = create();
    const c = f.componentInstance;
    c.hidden.set(true);
    vi.advanceTimersByTime(6500);
    expect(c.active()).toBe(0);
    c.pointerDown({ pointerType: 'touch', clientX: 200, clientY: 100 } as PointerEvent);
    c.pointerUp({ clientX: 100, clientY: 105 } as PointerEvent);
    expect(c.active()).toBe(1);
    c.pointerDown({ pointerType: 'touch', clientX: 200, clientY: 100 } as PointerEvent);
    c.pointerUp({ clientX: 100, clientY: 300 } as PointerEvent);
    expect(c.active()).toBe(1);
  });
  it('retains the caption and navigation if a photo fails', () => {
    const f = create();
    f.componentInstance.imageFailed(0);
    f.detectChanges();
    expect(f.nativeElement.querySelector('.slide.active img')).toBeNull();
    expect(f.nativeElement.querySelector('.slide.active').textContent).toContain(
      'Visuel momentanément indisponible',
    );
    expect(f.nativeElement.querySelector('.slide.active a').getAttribute('href')).toBe(
      '/solutions',
    );
  });
});
