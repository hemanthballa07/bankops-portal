import { TimeAgoPipe } from './time-ago.pipe';

describe('TimeAgoPipe', () => {
  const pipe = new TimeAgoPipe();

  it('renders minutes, hours, and days', () => {
    expect(pipe.transform(new Date(Date.now() - 5 * 60 * 1000).toISOString())).toBe('5m ago');
    expect(pipe.transform(new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString())).toBe('3h ago');
    expect(pipe.transform(new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString())).toBe('2d ago');
  });
});
