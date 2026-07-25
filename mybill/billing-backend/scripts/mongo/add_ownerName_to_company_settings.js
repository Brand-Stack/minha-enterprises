// MongoDB: optional one-time update for existing company_settings documents
// Usage: mongosh <connection-string> add_ownerName_to_company_settings.js
db.company_settings.updateMany(
  { ownerName: { $exists: false } },
  { $set: { ownerName: "" } }
);
