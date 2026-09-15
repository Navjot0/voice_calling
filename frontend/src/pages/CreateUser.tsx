import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { usersApi } from "../api/usersApi";
import { ErrorMessage } from "../components/ErrorMessage";
import { ApiRequestError } from "../types/api";

const EXTENSION_PATTERN = /^[0-9]{2,15}$/;
const MAX_LENGTH = 128;

interface FormErrors {
  extension?: string;
  password?: string;
  name?: string;
}

export function CreateUser() {
  const navigate = useNavigate();
  const [extension, setExtension] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [errors, setErrors] = useState<FormErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const validate = (): boolean => {
    const next: FormErrors = {};
    if (!extension.trim()) {
      next.extension = "Extension is required.";
    } else if (!EXTENSION_PATTERN.test(extension.trim())) {
      next.extension = "Extension must be numeric (2-15 digits).";
    }

    if (!password) {
      next.password = "Password is required.";
    } else if (password.length > MAX_LENGTH) {
      next.password = `Password must be at most ${MAX_LENGTH} characters.`;
    }

    if (!name.trim()) {
      next.name = "Name is required.";
    } else if (name.length > MAX_LENGTH) {
      next.name = `Name must be at most ${MAX_LENGTH} characters.`;
    }

    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (submitting) {
      return; // Guards against duplicate submissions from a double click.
    }
    setSubmitError(null);
    if (!validate()) {
      return;
    }

    setSubmitting(true);
    try {
      const created = await usersApi.createUser({
        extension: extension.trim(),
        password,
        name: name.trim(),
      });
      // The password is never needed again past this point - and is never
      // stored anywhere (state, localStorage, logs) beyond this request.
      setPassword("");
      navigate(`/users/${created.extension}`, { state: { justCreated: true } });
    } catch (err) {
      setSubmitError(err instanceof ApiRequestError ? err.message : "Failed to create extension.");
      setSubmitting(false);
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Create Extension</h1>
          <p className="page-subtitle">Provision a new SIP extension on the FreeSWITCH server.</p>
        </div>
      </div>

      <form className="form panel" onSubmit={handleSubmit} noValidate>
        {submitError && <ErrorMessage message={submitError} />}

        <div className="form-field">
          <label htmlFor="extension">Extension</label>
          <input
            id="extension"
            name="extension"
            type="text"
            inputMode="numeric"
            autoComplete="off"
            value={extension}
            onChange={(event) => setExtension(event.target.value)}
            aria-invalid={Boolean(errors.extension)}
            aria-describedby={errors.extension ? "extension-error" : undefined}
          />
          {errors.extension && (
            <p id="extension-error" className="field-error">
              {errors.extension}
            </p>
          )}
        </div>

        <div className="form-field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            aria-invalid={Boolean(errors.password)}
            aria-describedby={errors.password ? "password-error" : undefined}
          />
          {errors.password && (
            <p id="password-error" className="field-error">
              {errors.password}
            </p>
          )}
        </div>

        <div className="form-field">
          <label htmlFor="name">Name</label>
          <input
            id="name"
            name="name"
            type="text"
            autoComplete="off"
            value={name}
            onChange={(event) => setName(event.target.value)}
            aria-invalid={Boolean(errors.name)}
            aria-describedby={errors.name ? "name-error" : undefined}
          />
          {errors.name && (
            <p id="name-error" className="field-error">
              {errors.name}
            </p>
          )}
        </div>

        <div className="form-actions">
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? "Creating extension..." : "Create Extension"}
          </button>
        </div>
      </form>
    </div>
  );
}
