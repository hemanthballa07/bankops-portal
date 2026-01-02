import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

export interface User {
    username: string;
    roles: string[];
}

@Injectable({
    providedIn: 'root'
})
export class AuthService {
    private apiUrl = environment.apiUrl || 'http://localhost:8080/api';
    private AUTH_KEY = 'AUTH_BASIC_V2';
    private currentUserSubject = new BehaviorSubject<User | null>(null);
    public currentUser$ = this.currentUserSubject.asObservable();

    constructor(private http: HttpClient) {
        // Check if we have credentials on startup
        if (this.getAuthHeader()) {
            // Optimistically assume logged in, verify later if needed
            this.currentUserSubject.next({ username: 'User', roles: [] });
        }
    }

    login(username: string, password: string): Observable<any> {
        const authHeader = 'Basic ' + btoa(`${username}:${password}`);
        const headers = new HttpHeaders({ 'Authorization': authHeader });

        return this.http.get<any>(`${this.apiUrl}/whoami`, { headers }).pipe(
            tap(response => {
                sessionStorage.setItem(this.AUTH_KEY, authHeader);
                const user: User = {
                    username: response.username || username,
                    roles: response.roles || []
                };
                this.currentUserSubject.next(user);
            })
        );
    }

    logout() {
        sessionStorage.removeItem(this.AUTH_KEY);
        this.currentUserSubject.next(null);
        window.location.reload(); // Simple Reset
    }

    getAuthHeader(): string | null {
        return sessionStorage.getItem(this.AUTH_KEY);
    }

    isAuthenticated(): boolean {
        return !!this.getAuthHeader();
    }
}
