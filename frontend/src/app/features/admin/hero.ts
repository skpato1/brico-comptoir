import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AdminApi, adminError, HeroContent, HeroSlide } from '../../core/admin-api';
import { heroImageUrl } from '../store/hero-images';

@Component({
  selector: 'app-admin-hero',
  imports: [FormsModule],
  templateUrl: './hero.html',
})
export class AdminHero implements OnInit {
  readonly heroImageUrl = heroImageUrl;
  private readonly api = inject(AdminApi);
  private readonly destroy = inject(DestroyRef);
  readonly value = signal<HeroContent | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  ngOnInit() { this.load(); }
  load() {
    this.busy.set(true); this.error.set(''); this.success.set('');
    this.api.hero(true).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: value => { this.value.set(value); this.busy.set(false); },
      error: error => { this.busy.set(false); this.error.set(adminError(error)); },
    });
  }
  add() {
    const value = this.value();
    if (!value || value.slides.length >= 10) return;
    const slide: HeroSlide = {
      id: crypto.randomUUID(), visible: false, image: 'kits', label: '', title: '',
      description: '', alt: 'Illustration de projet, générée par IA.', link: '/catalogue',
      action: 'Découvrir', detail: '',
    };
    this.value.set({ ...value, slides: [...value.slides, slide] });
  }
  remove(index: number) {
    const value = this.value();
    if (value && value.slides.length > 1)
      this.value.set({ ...value, slides: value.slides.filter((_, i) => i !== index) });
  }
  move(index: number, direction: number) {
    const value = this.value();
    if (!value || index + direction < 0 || index + direction >= value.slides.length) return;
    const slides = [...value.slides];
    [slides[index], slides[index + direction]] = [slides[index + direction], slides[index]];
    this.value.set({ ...value, slides });
  }
  save(form: NgForm) {
    const value = this.value();
    this.error.set(''); this.success.set('');
    if (!value || form.invalid || !value.slides.some(slide => slide.visible)) {
      form.control.markAllAsTouched();
      this.error.set('Complétez tous les champs et gardez au moins une bannière visible.');
      return;
    }
    if (this.busy()) return;
    this.busy.set(true);
    this.api.saveHero(value).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: saved => { this.value.set(saved); this.busy.set(false); this.success.set('Bannières enregistrées.'); },
      error: error => { this.busy.set(false); this.error.set(adminError(error)); },
    });
  }
}
