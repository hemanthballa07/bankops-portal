import { AmountPipe } from './amount.pipe';

describe('AmountPipe', () => {
  const pipe = new AmountPipe();

  it('formats a number as USD currency', () => {
    expect(pipe.transform(1234.5)).toBe('$1,234.50');
    expect(pipe.transform(0)).toBe('$0.00');
  });
});
