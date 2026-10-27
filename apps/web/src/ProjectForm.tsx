import { useState } from "react";
import type { Project } from "@plotus/contracts";
import { Modal } from "./Modal";

export function ProjectForm({
  project,
  onClose,
  onSave,
}: {
  project?: Project;
  onClose: () => void;
  onSave: (project: Project) => void;
}) {
  const [client, setClient] = useState(Boolean(project?.client));
  return (
    <Modal title={project ? "Edit project" : "New project"} onClose={onClose}>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          const data = new FormData(event.currentTarget);
          const name = String(data.get("name") ?? "").trim();
          const location = String(data.get("location") ?? "").trim();
          if (!name || !location) return;
          onSave({
            id: project?.id ?? crypto.randomUUID(),
            name,
            location,
            areaSqFt: project?.areaSqFt ?? null,
            updatedAt: new Date().toISOString(),
            ...(client
              ? {
                  client: {
                    name: String(data.get("clientName") ?? "").trim(),
                    email: String(data.get("clientEmail") ?? "").trim(),
                    phone: String(data.get("clientPhone") ?? "").trim(),
                  },
                }
              : {}),
          });
        }}
      >
        <label className="field">
          Name
          <input
            autoFocus
            name="name"
            required
            maxLength={120}
            placeholder="Project name"
            defaultValue={project?.name}
            pattern=".*\S.*"
          />
        </label>
        <label className="field">
          Location
          <input
            name="location"
            required
            maxLength={200}
            placeholder="City, neighbourhood"
            defaultValue={project?.location}
            pattern=".*\S.*"
          />
        </label>
        <label className="switch-row">
          Client project?
          <input
            type="checkbox"
            role="switch"
            checked={client}
            onChange={(event) => setClient(event.target.checked)}
          />
          <span className="switch-art">
            <img
              src={`/assets/figma/${client ? "50117" : "0a0c5"}.svg`}
              alt=""
            />
          </span>
        </label>
        <fieldset className="client-fields" hidden={!client} disabled={!client}>
          <legend>Client information</legend>
          <label className="field">
            Client name
            <input
              name="clientName"
              required={client}
              maxLength={120}
              defaultValue={project?.client?.name}
              autoComplete="name"
            />
          </label>
          <label className="field">
            Email
            <input
              name="clientEmail"
              type="email"
              required={client}
              maxLength={254}
              defaultValue={project?.client?.email}
              autoComplete="email"
            />
          </label>
          <label className="field">
            Phone no.
            <input
              name="clientPhone"
              type="tel"
              maxLength={40}
              defaultValue={project?.client?.phone}
              autoComplete="tel"
            />
          </label>
        </fieldset>
        <div className="form-actions">
          <button className="button outline" type="button" onClick={onClose}>
            Cancel
          </button>
          <button className="button primary" type="submit">
            {project ? "Save changes" : "Create project"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
