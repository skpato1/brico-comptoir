import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable, timeout } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ApiHealth {
  private readonly http = inject(HttpClient);

  check(): Observable<void> {
    return this.http.get<{ status?: string }>('/api/v1/health').pipe(
      timeout(5000),
      map((response) => {
        if (response?.status !== 'UP') {
          throw new Error('API unavailable');
        }
      }),
    );
  }
}
