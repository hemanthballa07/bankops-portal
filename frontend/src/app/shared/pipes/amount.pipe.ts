import { Pipe, PipeTransform } from '@angular/core';

/** Formats a number as USD currency (e.g. 1234.5 -> "$1,234.50"). */
@Pipe({ name: 'amount', standalone: true })
export class AmountPipe implements PipeTransform {
  transform(amount: number): string {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(amount);
  }
}
