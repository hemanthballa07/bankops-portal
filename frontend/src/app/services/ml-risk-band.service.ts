import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { MlRiskBandConfig } from '../models/ml-risk-band.model';

@Injectable({ providedIn: 'root' })
export class MlRiskBandService {
  private readonly apiUrl = `${environment.apiUrl}/ml-risk-bands`;

  constructor(private http: HttpClient) {}

  getBands(): Observable<MlRiskBandConfig> {
    return this.http.get<MlRiskBandConfig>(this.apiUrl);
  }

  update(medThreshold: number, highThreshold: number): Observable<MlRiskBandConfig> {
    return this.http.put<MlRiskBandConfig>(this.apiUrl, { medThreshold, highThreshold });
  }
}
