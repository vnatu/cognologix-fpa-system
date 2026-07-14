import axios from 'axios';
import type { DateFormatOption } from '@/utils/formatDate';

export const fetchDateFormat = (): Promise<DateFormatOption> =>
  axios
    .get<{ format: DateFormatOption }>('/api/general/config/date-format')
    .then((r) => r.data.format);

export const updateDateFormat = (
  format: DateFormatOption,
): Promise<DateFormatOption> =>
  axios
    .put<{ format: DateFormatOption }>('/api/general/config/date-format', {
      format,
    })
    .then((r) => r.data.format);
