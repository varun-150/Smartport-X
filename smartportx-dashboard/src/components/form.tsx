import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react';

interface FieldProps {
  label: ReactNode;
  required?: boolean;
  error?: string | null;
  hint?: ReactNode;
  children: ReactNode;
}

export function Field({ label, required, error, hint, children }: FieldProps) {
  return (
    <div className="field">
      <label>
        {label}
        {required ? <span className="req"> *</span> : null}
      </label>
      {children}
      {error ? <span className="field-err" role="alert">{error}</span> : null}
      {hint ? <span className="muted">{hint}</span> : null}
    </div>
  );
}

interface TextInputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: ReactNode;
  required?: boolean;
  error?: string | null;
  hint?: ReactNode;
}

export function TextInput({ label, required, error, hint, className = '', id, ...rest }: TextInputProps) {
  return (
    <Field label={label} required={required} error={error} hint={hint}>
      <input id={id} className={`input ${className}`.trim()} {...rest} />
    </Field>
  );
}

interface SelectOption {
  value: string;
  label: string;
}

interface SelectProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'children'> {
  label: ReactNode;
  required?: boolean;
  error?: string | null;
  hint?: ReactNode;
  options: SelectOption[];
  placeholder?: string;
}

export function Select({ label, required, error, hint, options, placeholder, className = '', id, ...rest }: SelectProps) {
  return (
    <Field label={label} required={required} error={error} hint={hint}>
      <select id={id} className={`select ${className}`.trim()} {...rest}>
        {placeholder ? (
          <option value="" disabled>
            {placeholder}
          </option>
        ) : null}
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </Field>
  );
}

interface TextAreaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: ReactNode;
  required?: boolean;
  error?: string | null;
  hint?: ReactNode;
}

export function TextArea({ label, required, error, hint, className = '', id, ...rest }: TextAreaProps) {
  return (
    <Field label={label} required={required} error={error} hint={hint}>
      <textarea id={id} className={`textarea ${className}`.trim()} {...rest} />
    </Field>
  );
}

interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: ReactNode;
}

export function Checkbox({ label, defaultChecked, ...rest }: CheckboxProps) {
  return (
    <label className="checkbox-row">
      <input type="checkbox" defaultChecked={defaultChecked} {...rest} />
      <span>{label}</span>
    </label>
  );
}