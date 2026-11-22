create table if not exists public.fixes (
  row_id bigserial primary key,
  client_id bigint,
  lat double precision not null,
  lon double precision not null,
  accuracy double precision,
  altitude double precision,
  vertical_accuracy double precision,
  speed double precision,
  bearing double precision,
  provider text,
  is_mock boolean,
  activity text,
  battery_pct int,
  charging boolean,
  source text,
  captured_at bigint not null,
  elapsed_nanos bigint,
  device text,
  inserted_at timestamptz default now()
);
create index if not exists fixes_t on public.fixes (captured_at desc);
alter table public.fixes enable row level security;
drop policy if exists fixes_ins on public.fixes;
create policy fixes_ins on public.fixes for insert to anon with check (true);
drop policy if exists fixes_sel on public.fixes;
create policy fixes_sel on public.fixes for select to anon using (true);