import { useEffect, useState } from 'react';
import { Loader2, ShieldCheck } from 'lucide-react';

import { errorMessage } from '../api/client';
import { auth } from '../api/endpoints';
import { Banner, PageHeader, SkeletonCard } from '../components/ui';
import { useAuth } from '../context/useAuth';
import { humanise, money } from '../lib/format';

interface Profile {
  customerNumber: string;
  firstName: string;
  lastName: string;
  email: string;
  mobile: string;
  addressLine: string | null;
  city: string | null;
  state: string | null;
  pincode: string | null;
  declaredMonthlyIncome: number | null;
  kycStatus: string;
}

const ID_PROOFS = ['AADHAAR', 'PAN', 'PASSPORT', 'DRIVING_LICENCE', 'VOTER_ID'];
const ADDRESS_PROOFS = ['UTILITY_BILL', 'BANK_STATEMENT', 'RENT_AGREEMENT', 'PASSPORT'];

/** Profile, KYC submission and session details. */
export default function ProfilePage() {
  const { session } = useAuth();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);

  const [form, setForm] = useState({
    addressLine: '',
    city: '',
    state: '',
    pincode: '',
    declaredMonthlyIncome: '',
  });

  const [kyc, setKyc] = useState({
    idProofType: 'AADHAAR',
    idProofNumber: '',
    addressProofType: 'UTILITY_BILL',
  });

  useEffect(() => {
    auth
      .profile()
      .then((data: Profile) => {
        setProfile(data);
        setForm({
          addressLine: data.addressLine ?? '',
          city: data.city ?? '',
          state: data.state ?? '',
          pincode: data.pincode ?? '',
          declaredMonthlyIncome: data.declaredMonthlyIncome?.toString() ?? '',
        });
      })
      .catch((caught) => setError(errorMessage(caught, 'Could not load your profile.')));
  }, []);

  async function save(event: React.FormEvent) {
    event.preventDefault();
    setError('');
    setNotice('');
    setBusy(true);
    try {
      const updated: Profile = await auth.updateProfile({
        ...form,
        declaredMonthlyIncome: form.declaredMonthlyIncome
          ? Number(form.declaredMonthlyIncome).toFixed(2)
          : null,
      });
      setProfile(updated);
      setNotice('Your details were saved.');
    } catch (caught) {
      setError(errorMessage(caught, 'Could not save your details.'));
    } finally {
      setBusy(false);
    }
  }

  async function submitKyc(event: React.FormEvent) {
    event.preventDefault();
    setError('');
    setNotice('');
    setBusy(true);
    try {
      await auth.submitKyc(kyc);
      const refreshed: Profile = await auth.profile();
      setProfile(refreshed);
      setKyc((current) => ({ ...current, idProofNumber: '' }));
      setNotice('Documents submitted. A branch officer will review them.');
    } catch (caught) {
      setError(errorMessage(caught, 'Could not submit your documents.'));
    } finally {
      setBusy(false);
    }
  }

  if (!profile) {
    return (
      <>
        <PageHeader title="Profile and settings" />
        <SkeletonCard lines={5} />
      </>
    );
  }

  const verified = profile.kycStatus === 'VERIFIED';

  return (
    <>
      <PageHeader
        title="Profile and settings"
        subtitle={`Customer ${profile.customerNumber}`}
      />

      <div className="grid gap-5 lg:grid-cols-3">
        <div className="space-y-5 lg:col-span-2">
          {error && <Banner tone="error">{error}</Banner>}
          {notice && <Banner tone="success">{notice}</Banner>}

          <form onSubmit={save} className="card space-y-4">
            <h2 className="font-semibold text-navy-900 dark:text-white">Your details</h2>

            <div className="grid gap-4 sm:grid-cols-2">
              <Readonly label="Name" value={`${profile.firstName} ${profile.lastName}`} />
              <Readonly label="Customer number" value={profile.customerNumber} />
              <Readonly label="Email" value={profile.email} />
              <Readonly label="Mobile" value={profile.mobile} />
            </div>

            <p className="rounded-xl bg-navy-50 p-3 text-xs text-navy-600 dark:bg-navy-950 dark:text-navy-400">
              Email and mobile cannot be changed here. Both are where your one-time codes are
              delivered, so changing either is a security event that needs its own verified flow.
            </p>

            <div>
              <label className="label" htmlFor="addressLine">
                Address
              </label>
              <input
                id="addressLine"
                className="input"
                value={form.addressLine}
                onChange={(event) => setForm((c) => ({ ...c, addressLine: event.target.value }))}
              />
            </div>

            <div className="grid gap-4 sm:grid-cols-3">
              <div>
                <label className="label" htmlFor="city">
                  City
                </label>
                <input
                  id="city"
                  className="input"
                  value={form.city}
                  onChange={(event) => setForm((c) => ({ ...c, city: event.target.value }))}
                />
              </div>
              <div>
                <label className="label" htmlFor="state">
                  State
                </label>
                <input
                  id="state"
                  className="input"
                  value={form.state}
                  onChange={(event) => setForm((c) => ({ ...c, state: event.target.value }))}
                />
              </div>
              <div>
                <label className="label" htmlFor="pincode">
                  PIN code
                </label>
                <input
                  id="pincode"
                  inputMode="numeric"
                  maxLength={6}
                  className="input tabular"
                  value={form.pincode}
                  onChange={(event) =>
                    setForm((c) => ({ ...c, pincode: event.target.value.replace(/\D/g, '') }))
                  }
                />
              </div>
            </div>

            <div>
              <label className="label" htmlFor="income">
                Monthly income
              </label>
              <input
                id="income"
                inputMode="decimal"
                className="input tabular"
                value={form.declaredMonthlyIncome}
                onChange={(event) =>
                  setForm((c) => ({
                    ...c,
                    declaredMonthlyIncome: event.target.value.replace(/[^\d.]/g, ''),
                  }))
                }
              />
              <p className="mt-1.5 text-xs text-navy-500 dark:text-navy-400">
                Used by the financial health score when no salary credit is visible in your
                transactions.
              </p>
            </div>

            <button type="submit" className="btn-primary" disabled={busy}>
              {busy && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
              Save details
            </button>
          </form>

          {!verified && (
            <form onSubmit={submitKyc} className="card space-y-4">
              <h2 className="font-semibold text-navy-900 dark:text-white">KYC verification</h2>
              <p className="text-sm text-navy-600 dark:text-navy-300">
                Transfers stay locked until a branch officer verifies your documents. Only the last
                four characters of the document number are stored.
              </p>

              <div className="grid gap-4 sm:grid-cols-2">
                <div>
                  <label className="label" htmlFor="idProofType">
                    ID proof type
                  </label>
                  <select
                    id="idProofType"
                    className="input"
                    value={kyc.idProofType}
                    onChange={(event) => setKyc((c) => ({ ...c, idProofType: event.target.value }))}
                  >
                    {ID_PROOFS.map((item) => (
                      <option key={item} value={item}>
                        {humanise(item)}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="label" htmlFor="idProofNumber">
                    ID proof number
                  </label>
                  <input
                    id="idProofNumber"
                    required
                    minLength={4}
                    className="input tabular"
                    value={kyc.idProofNumber}
                    onChange={(event) =>
                      setKyc((c) => ({ ...c, idProofNumber: event.target.value }))
                    }
                  />
                </div>
                <div>
                  <label className="label" htmlFor="addressProofType">
                    Address proof type
                  </label>
                  <select
                    id="addressProofType"
                    className="input"
                    value={kyc.addressProofType}
                    onChange={(event) =>
                      setKyc((c) => ({ ...c, addressProofType: event.target.value }))
                    }
                  >
                    {ADDRESS_PROOFS.map((item) => (
                      <option key={item} value={item}>
                        {humanise(item)}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <button type="submit" className="btn-primary" disabled={busy}>
                {busy && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
                Submit documents
              </button>
            </form>
          )}
        </div>

        <div className="space-y-5">
          <div className="card">
            <h2 className="mb-3 font-semibold text-navy-900 dark:text-white">KYC status</h2>
            <div className="flex items-center gap-2">
              <ShieldCheck
                className={`h-5 w-5 ${
                  verified ? 'text-emerald-600 dark:text-emerald-400' : 'text-amber-500'
                }`}
                aria-hidden
              />
              <p className="font-semibold text-navy-900 dark:text-white">
                {humanise(profile.kycStatus)}
              </p>
            </div>
            <p className="mt-2 text-sm text-navy-600 dark:text-navy-300">
              {verified
                ? 'Your identity is verified. Transfers are available.'
                : 'Transfers are locked until verification completes.'}
            </p>
          </div>

          <div className="card">
            <h2 className="mb-3 font-semibold text-navy-900 dark:text-white">Session</h2>
            <dl className="space-y-2 text-sm">
              <div className="flex justify-between gap-3">
                <dt className="text-navy-500 dark:text-navy-400">Signed in as</dt>
                <dd className="truncate font-medium text-navy-900 dark:text-white">
                  {session?.username}
                </dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="text-navy-500 dark:text-navy-400">Roles</dt>
                <dd className="text-right font-medium text-navy-900 dark:text-white">
                  {session?.roles.join(', ')}
                </dd>
              </div>
              {profile.declaredMonthlyIncome !== null && (
                <div className="flex justify-between gap-3">
                  <dt className="text-navy-500 dark:text-navy-400">Declared income</dt>
                  <dd className="tabular font-medium text-navy-900 dark:text-white">
                    {money(profile.declaredMonthlyIncome)}
                  </dd>
                </div>
              )}
            </dl>
            <p className="mt-3 text-xs text-navy-500 dark:text-navy-400">
              Your access token is held in memory only, so refreshing the page signs you out. That
              is deliberate: a token in browser storage can be read by injected scripts.
            </p>
          </div>
        </div>
      </div>
    </>
  );
}

function Readonly({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="label">{label}</p>
      <p className="rounded-xl bg-navy-50 px-3.5 py-2.5 text-sm text-navy-800 dark:bg-navy-950 dark:text-navy-100">
        {value}
      </p>
    </div>
  );
}
