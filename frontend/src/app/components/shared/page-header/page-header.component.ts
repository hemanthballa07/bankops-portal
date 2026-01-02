import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterModule } from '@angular/router';

export interface Breadcrumb {
    label: string;
    route?: string;
}

export interface PageAction {
    label: string;
    icon?: string;
    handler: () => void;
    color?: 'primary' | 'accent' | 'warn';
}

@Component({
    selector: 'app-page-header',
    standalone: true,
    imports: [CommonModule, MatButtonModule, MatIconModule, RouterModule],
    templateUrl: './page-header.component.html',
    styleUrls: ['./page-header.component.scss']
})
export class PageHeaderComponent {
    @Input() title!: string;
    @Input() subtitle?: string;
    @Input() breadcrumbs?: Breadcrumb[];
    @Input() primaryAction?: PageAction;
    @Input() secondaryActions?: PageAction[];
}
